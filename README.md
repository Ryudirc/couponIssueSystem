# Coupon Issue System · Single-Flight

**캐시가 비는 순간 몰리는 DB 조회를 대표 요청으로 모으는 단계입니다.** Redis Lua 선차감·Kafka 비동기 저장은 유지하고, 발급 시작 시각·유효 일수 등 쿠폰 정책의 반복 조회를 줄입니다.

> 현재 단계: `feature/cache_singleFlight` · 코드 기준: [970f77b](https://github.com/Ryudirc/couponIssueSystem/commit/970f77b). 강의 기반 학습 프로젝트의 브랜치별 구현 기록입니다.

## 브랜치별 발전 과정

| 단계 | 브랜치 | 집중한 문제 | 추가한 방법 |
|---|---|---|---|
| 1 | [main](https://github.com/Ryudirc/couponIssueSystem/tree/main) | 발급 요청마다 DB 저장을 기다리는 부담 | Redis Lua 선차감 + Kafka 비동기 저장 |
| 2 | [single-flight](https://github.com/Ryudirc/couponIssueSystem/tree/feature/cache_singleFlight) | 캐시가 비면 같은 쿠폰을 동시에 DB에서 조회 | 대표 요청 하나가 캐시를 채우고 나머지는 대기 |
| 3 | [SWR](https://github.com/Ryudirc/couponIssueSystem/tree/feature/swr) | 캐시 갱신 중 응답이 기다리는 문제 | 기존 값을 먼저 반환하고 뒤에서 갱신 |
| 4 | [Caffeine](https://github.com/Ryudirc/couponIssueSystem/tree/feature/caffeine_cache) | 매진 후에도 반복되는 Redis 요청 | 프로세스 내부에서 매진 여부를 짧게 캐시 |

각 브랜치는 앞 단계의 구현을 이어받습니다. **발급 재고**, **쿠폰 발급 정책 캐시**, **매진 여부 캐시**는 서로 다른 데이터입니다. 정책 캐시가 오래됐더라도 실제 재고 확인·차감은 Redis Lua에서 수행합니다.

## 문제와 선택

캐시가 만료되면 같은 쿠폰을 요청한 여러 스레드가 동시에 DB로 향할 수 있습니다. 이를 **캐시 스탬피드**라고 합니다. 여기서는 Redis 락을 얻은 요청 하나가 정책을 읽어 캐시를 채우고, 나머지는 결과를 기다리도록 했습니다.

```mermaid
flowchart TD
    A[발급 정책 조회] --> B[Redis Lua: 캐시와 락 확인]
    B -->|HIT| C[캐시 반환]
    B -->|LOAD: 락 획득| D[DB 정책 조회]
    D --> E[Redis 캐시 저장]
    E --> F[내 토큰의 락 해제]
    B -->|WAIT| G[20ms 대기 후 재확인]
    G --> B
    C --> H[시작 시각 확인 → 재고 Lua → Kafka → Worker DB 저장]
    F --> H
```

- **HIT:** 저장된 정책을 바로 사용합니다.
- **LOAD:** UUID 토큰으로 락을 획득한 요청이 DB를 조회하고 캐시를 저장합니다.
- **WAIT:** 다른 요청이 채우는 동안 20ms씩 최대 50회 반복합니다. 소진하면 예외를 던집니다.
- 락은 3초 후 만료됩니다. 해제 시 토큰을 비교해 다른 요청의 락을 지우지 않습니다.

대표 요청이 락 유효 시간 안에 끝나는 경우 중복 조회를 줄이는 구조입니다. **항상 DB 조회가 한 번뿐임을 보장하지는 않습니다.** 조회가 3초보다 길면 다른 요청이 다시 락을 얻을 수 있습니다.

### 핵심 코드

| 책임 | 코드 |
|---|---|
| 정책 조회 분리 | [CouponIssuePolicyReader](src/main/kotlin/com/travio/coupon/application/CouponIssuePolicyReader.kt), [CouponIssuePolicy](src/main/kotlin/com/travio/coupon/application/CouponIssuePolicy.kt) |
| HIT·LOAD·WAIT 처리 | [CouponCacheRepository](src/main/kotlin/com/travio/coupon/infrastructure/cache/CouponCacheRepository.kt) |
| 캐시 조회와 락 획득 | [cache-single-flight.lua](src/main/resources/lua/cache-single-flight.lua) |
| 내 락만 해제 | [release-lock.lua](src/main/resources/lua/release-lock.lua) |
| 조회 횟수 관찰 | [CacheMetrics](src/main/kotlin/com/travio/coupon/application/CacheMetrics.kt) |

### 설정

| 항목 | 앱 단독 기본값 | Compose 기본값 |
|---|---:|---:|
| 정책 캐시 TTL | 10,000ms | 1,000ms |
| DB 조회 인위적 지연 | 0ms | 100ms |
| 락 만료 / 대기 | 3,000ms / 20ms×50회 | 동일 |

Compose의 짧은 TTL과 인위적 지연은 차이를 관찰하기 위한 **실험 설정**입니다. 운영 DB 성능 수치가 아닙니다.

## API

사용자 식별은 학습용 `X-User-Id` 헤더를 사용합니다. JWT 인증을 구현한 API가 아닙니다.

| Method | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/coupons` | 쿠폰 이름·수량·유효 일수·선택 시작 시각 | 201, 생성된 쿠폰 정보 |
| POST | `/api/coupons/{couponId}/issue` | `X-User-Id` | 200, 발급 요청 정보 |
| GET | `/api/users/me/issuances` | `X-User-Id` | 200, DB에 저장된 발급 내역 배열 |
| POST | `/api/issuances/{issuanceId}/use` | `X-User-Id` | 200, 사용 처리한 발급 내역 |

발급 API의 200은 **DB 저장 완료를 뜻하지 않습니다.** 응답의 `id`는 아직 `null`이며, Worker 저장 후 목록 조회에서 실제 발급 ID를 얻습니다. 시작 전·매진·중복 발급은 409, 없는 쿠폰·발급 내역은 404입니다.

```sh
# 시작 시각을 생략하면 즉시 발급 가능
curl -X POST http://localhost:8080/api/coupons \
  -H 'Content-Type: application/json' \
  -d '{"name":"선착순 쿠폰","totalQuantity":100,"validityDays":7}'

# 생성 응답의 id를 couponId로 사용
curl -X POST http://localhost:8080/api/coupons/1/issue -H 'X-User-Id: 101'
curl http://localhost:8080/api/users/me/issuances -H 'X-User-Id: 101'
```

## 실행

**필수:** JDK 25, Docker Compose. 부하 실험은 추가로 Bash(Git Bash/WSL), k6, curl, jq가 필요합니다.

- Kotlin 2.3.21 / Spring Boot 4.1.1 / Spring Data JPA
- MySQL 8.4 / Redis 8.0 / Kafka 3.8.0(KRaft)
- Gradle Wrapper / Jib 3.5.4

현재 브랜치 루트에서 실행합니다. 기본 포트 `8080·3306·6379·9094`가 다른 프로젝트와 충돌하지 않아야 합니다.

```sh
docker compose up -d mysql redis kafka
./gradlew bootRun
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용합니다. 컨테이너로 앱까지 실행하려면 다음 명령을 사용합니다.

```sh
./gradlew jibDockerBuild
docker compose up -d
```

Compose는 이미지를 직접 빌드하지 않습니다. **브랜치를 바꿨다면 Jib로 다시 빌드**해야 해당 브랜치 코드가 실행됩니다. Jib 설정은 Docker Desktop의 `desktop` credential helper를 사용합니다. 접속 정보는 [application.yaml](src/main/resources/application.yaml), 컨테이너 구성은 [docker-compose.yml](docker-compose.yml)을 참고합니다.

## 검증 방법과 기록 범위

| 도구 | 확인할 내용 |
|---|---|
| [초과 발급 실험](scripts/load/run.sh) | 발급 행 수가 총량 이하인지, DB 카운터와 실제 행 수가 일치하는지 |
| [응답 부하 실험](scripts/response/run.sh) | 발급 API 지연과 Worker 저장 이후의 DB 상태 |
| [Spring 테스트](src/test/kotlin/com/travio/coupon/CouponApplicationTests.kt) | 애플리케이션 컨텍스트 기동 (`contextLoads` 1개) |

```sh
./gradlew test
bash scripts/response/run.sh --once
```

**주의:** 실험 스크립트는 `coupon·issuance` 테이블을 TRUNCATE하고 Redis DB를 FLUSHDB합니다. 전용 로컬 환경에서만 실행하며, 이전 Kafka 메시지의 소비도 끝난 상태에서 측정해야 합니다. DB 행 수가 잠시 변하지 않는다는 사실만으로 전체 메시지 처리가 끝났다고 단정할 수는 없습니다.

이 README 갱신에서는 코드·설정·스크립트·Git 이력을 대조했으며, 앱 기동·자동화 테스트·k6 부하 실험은 실행하지 않았습니다. 스크립트의 부하량과 p99 기준은 **실험 조건·목표이지 달성 결과가 아닙니다.** 성능 개선율과 장애 복구 보장을 제시하려면 실행 환경과 결과를 별도로 기록해야 합니다.

### 캐시 실험

```sh
bash scripts/cache/run.sh --once policy
bash scripts/cache/run.sh --once sellout
curl http://localhost:8080/metrics/cache
```

| API / 지표 | 의미 |
|---|---|
| `GET /metrics/cache` | 현재 프로세스의 누적 카운터 조회 |
| `POST /metrics/cache/reset` | 측정 카운터만 초기화; 캐시 삭제 아님 |
| `couponDbReads` | 정책 loader 실행 횟수; DB 조회 실패 시도도 포함 |
| `couponCacheHits` | 캐시 정책 반환 횟수 |
| `soldOutRedisExists` | 매진 플래그 Redis 조회 횟수 |
| `soldOutFastPathHits` | 로컬 캐시에서 매진으로 거절한 횟수 |

카운터는 `AtomicLong` 기반으로 서버 재시작 시 초기화되며, 다중 인스턴스 합산 지표가 아닙니다. 매진 지표는 Caffeine 단계에서 실제 증가합니다.

정책 실험은 시작 전 쿠폰에 500 요청/초를 30초간 보내 조회 경로를 관찰합니다. 매진 실험은 매진 후 4,000 요청/초를 30초간 보냅니다. k6가 요구하는 부하를 실제로 만들었는지도 함께 확인해야 합니다. 캐시 실험 러너는 k6 실패 코드를 무시하므로, 스크립트 종료만으로 성공을 판단하지 않습니다.

## 현재 한계와 후속 과제

- Redis 선차감과 Kafka 전송·MySQL 저장은 하나의 트랜잭션이 아닙니다. Producer는 `send()` 완료를 기다리지 않고 Outbox도 없어, 전송 실패 시 재고와 DB 내역이 어긋날 수 있습니다.
- DB의 `(user_id, coupon_id)` Unique 제약은 중복 저장을 막지만, Writer가 모든 `DataIntegrityViolationException`을 중복으로 간주하므로 다른 무결성 오류를 구분해야 합니다.
- 현재 발급자 Redis Set 키는 쿠폰 ID가 아닌 사용자 ID를 사용합니다. 따라서 서로 다른 쿠폰을 같은 사용자가 발급받는 경우에도 중복으로 판단할 수 있어 수정·검증이 필요합니다.
- `X-User-Id`는 인증되지 않은 입력입니다. 쿠폰 사용 시 소유권 검사와 동시 사용 제어도 보강해야 합니다. 현재 코드를 운영용 보안·정합성 완성본으로 보지 않습니다.

- 캐시를 채울 때까지 요청 스레드가 기다립니다. 대기 한도 소진·Redis 장애·3초를 넘는 loader의 처리는 별도 검증이 필요합니다.

## 다음 단계

DB 조회가 줄어도 갱신 중 대기는 남습니다. 다음 [SWR 브랜치](https://github.com/Ryudirc/couponIssueSystem/tree/feature/swr)는 사용 가능한 기존 정책을 먼저 반환하고 갱신을 뒤로 보냅니다.
