package ru.quipy.bankDemo.transfers.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.quipy.core.EventSourcingService
import ru.quipy.core.EventSourcingServiceFactory
import ru.quipy.bankDemo.transfers.api.TransferAggregate
import ru.quipy.bankDemo.transfers.logic.Transfer
import java.util.UUID

@Configuration
class TransferBoundedContextConfig {

    @Autowired
    private lateinit var eventSourcingServiceFactory: EventSourcingServiceFactory

    @Bean
    fun transactionEsService(): EventSourcingService<UUID, TransferAggregate, Transfer> =
        eventSourcingServiceFactory.create()
}