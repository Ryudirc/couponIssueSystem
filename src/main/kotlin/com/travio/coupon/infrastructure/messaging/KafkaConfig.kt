package com.travio.coupon.infrastructure.messaging

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.JacksonMapperUtils
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer
import org.springframework.kafka.support.serializer.JacksonJsonSerializer
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableKafka
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
) {

    private val jsonMapper: JsonMapper = JacksonMapperUtils.enhancedJsonMapper()

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.ACKS_CONFIG to "1",
        )
        return DefaultKafkaProducerFactory(props, StringSerializer(), JacksonJsonSerializer<Any>(jsonMapper))
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to IssuanceTopics.CONSUMER_GROUP,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest", //부하 테스트/로컬 편의용. 운영에서는 "latest" 또는 "none"으로 바꿀 것.
        )
        val jsonDelegate = JacksonJsonDeserializer(IssuanceRequested::class.java, jsonMapper).apply {
            addTrustedPackages("com.travio.coupon.infrastructure.messaging")
        }
        @Suppress("UNCHECKED_CAST")
        val valueDeserializer = ErrorHandlingDeserializer(jsonDelegate) as ErrorHandlingDeserializer<Any>
        //ErrorHandlingDeserializer 로 감싸야 깨진 payload 가 DLT 로 정상 격리된다.

        return DefaultKafkaConsumerFactory(props, StringDeserializer(), valueDeserializer)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Any>,
        errorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }
}