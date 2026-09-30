package com.travio.coupon.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class IssuanceWriter(
    private val issuanceTransactionalWriter: IssuanceTransactionalWriter,
) {

    fun write(event: IssuanceRequested) {

        try {
            // 만약 여기에 직접 insertAndIncrement 메서드의 내용이 있었더라면 write 메서드는 곧 @Transactional 메서드가 되었을거고
            // 트랜잭션 메서드는 AOP에 의해 프록시객체가 생성되어서 실제 DB작업 전후로 커넥션을 얻어오고, 트랜잭션을 열고 닫고 commit 하는 코드가
            // 자동으로 프록시객체에 생성되어서 작업을 진행하게 되는데, 만약 유니크 제약조건위배로 인해 예외가 발생하면 여기에서 catch가 조용히 먹고
            // 예외가 없는 흐름으로 바뀌게 되면서, 프록시 객체의 마지막 부분인 commit까지 도달하게 되었으나, 실제론 롤백이 되어야 했으니 UnexpectedRollbackException
            // 예외가 터져버릴 수 밖에 없게된다. 따라서, 트랜잭션 메서드를 클래스로 한번 감싸서, 그 안에서 트랜잭션 메서드를 실행해서 동일한 유니크 제약조건 위배 예외가 발생해서
            // IssuanceWriter까지 전파되더라도, 내부 insertAndIncrement에서 발생한 트랜잭션은 롤백될것이고, 바깥쪽 catch에서 잡고 끝낼 수 있으니 안전한 코드라고 볼 수 있겠음.
            issuanceTransactionalWriter.insertAndIncrement(event)
        }catch (e: DataIntegrityViolationException) {
            log.debug {  "UNIQUE 위반은 멱등 처리: couponId=${event.couponId}, userId=${event.userId}"}
        }
    }

}