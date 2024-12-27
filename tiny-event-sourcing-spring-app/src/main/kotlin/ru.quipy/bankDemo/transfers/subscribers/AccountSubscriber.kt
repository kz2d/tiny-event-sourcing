package ru.quipy.bankDemo.transfers.subscribers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.quipy.core.EventSourcingService
import ru.quipy.bankDemo.accounts.api.AccountAggregate
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferDepositFailedEvent
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferDepositSuccessEvent
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferEvent
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferWithdrawFailedEvent
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferWithdrawSuccessEvent
import ru.quipy.bankDemo.transfers.api.TransferAggregate
import ru.quipy.bankDemo.transfers.logic.Transfer
import ru.quipy.saga.SagaManager
import ru.quipy.streams.AggregateSubscriptionsManager
import java.util.UUID
import javax.annotation.PostConstruct

@Component
class AccountSubscriber(
    private val subscriptionsManager: AggregateSubscriptionsManager,
    private val transactionEsService: EventSourcingService<UUID, TransferAggregate, Transfer>,
    val sagaManager: SagaManager
) {
    private val logger: Logger = LoggerFactory.getLogger(AccountSubscriber::class.java)

    @PostConstruct
    fun init() {
        subscriptionsManager.createSubscriber(AccountAggregate::class, "transfers::accounts-subscriber") {
            println("message")
            `when`(ExternalAccountTransferEvent::class) { event ->
                println("transaction started")
                val sagaContext = sagaManager
                    .launchSaga("TRANSFER2", "start transfer money")
                    .sagaContext()
                transactionEsService.create{
                    it.createNew(event.transactionId)
                };

                transactionEsService.update(event.transactionId){
                    it.withdrawMoneyFrom(
                        accountIdFrom = event.accountIdFrom,
                        bankAccountIdFrom = event.bankAccountIdFrom,
                        accountIdTo = event.accountIdTo,
                        bankAccountIdTo = event.bankAccountIdTo,
                        transactionId = event.transactionId,
                        transferAmount = event.transferAmount
                    )
                }
            }
            `when`(ExternalAccountTransferWithdrawSuccessEvent::class) { event ->
                transactionEsService.update(event.transactionId) {
                    it.depositMoneyTo(
                        accountIdFrom = event.accountIdFrom,
                        bankAccountIdFrom = event.bankAccountIdFrom,
                        accountIdTo = event.accountIdTo,
                        bankAccountIdTo = event.bankAccountIdTo,
                        transactionId = event.transactionId,
                        transferAmount = event.transferAmount
                    )
                }
            }
            `when`(ExternalAccountTransferDepositFailedEvent::class) { event ->
                transactionEsService.update(event.transactionId) {
                    it.rollbackWithdrawMoney(
                        accountIdFrom = event.accountIdFrom,
                        bankAccountIdFrom = event.bankAccountIdFrom,
                        accountIdTo = event.accountIdTo,
                        bankAccountIdTo = event.bankAccountIdTo,
                        transactionId = event.transactionId,
                        transferAmount = event.transferAmount
                    )
                }
            }
            `when`(ExternalAccountTransferDepositSuccessEvent::class) { event ->
                transactionEsService.update(event.transactionId) {
                    it.notifyTransferSuccess(event.transactionId)
                }
            }
            `when`(ExternalAccountTransferWithdrawFailedEvent::class) { event ->
                logger.info(event.toString())
//                transactionEsService.update(event.transactionId) {
//                    TODO("error event -- catch on account side")
//                }
            }

        }
    }
}