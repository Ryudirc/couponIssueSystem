package com.travio.coupon.infrastructure.messaging

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor


const val ISSUANCE_TASK_EXECUTOR = "issuanceTaskExecutor"

@Configuration
@EnableAsync
class AsyncIssuanceConfig {

    @Bean(name = [ISSUANCE_TASK_EXECUTOR])
    fun issuanceTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
            .apply {
                corePoolSize = 1
                maxPoolSize = 1
                queueCapacity = 10_000
                setThreadNamePrefix("issuance-async-")
                setWaitForTasksToCompleteOnShutdown(true) // 갑자기 app이 종료될때 thead pool에서 작업중인 친구들은 모두 끝내고 종료하도록함
                setAwaitTerminationSeconds(30) // 30초 정도 여유를 두고 종료
                initialize() // 시작
            }
        return executor
    }

}