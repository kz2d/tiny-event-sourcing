package ru.quipy.bankDemo.transfers.logic

import ru.quipy.bankDemo.accounts.api.AccountCreatedEvent
import ru.quipy.bankDemo.transfers.api.*
import ru.quipy.core.annotations.StateTransitionFunc
import ru.quipy.domain.AggregateState
import ru.quipy.domain.Event
import java.math.BigDecimal
import java.util.UUID

class Transfer : AggregateState<UUID, TransferAggregate> {
    lateinit var transferId: UUID
    var status = TransferState.CREATED

    override fun getId(): UUID = transferId

   fun createNew(transferId: UUID) : TrasferCreatedEvent{
       return TrasferCreatedEvent(transferId)
   }

    fun withdrawMoneyFrom(
        accountIdFrom: UUID,
        bankAccountIdFrom: UUID,
        transferAmount: BigDecimal,
        accountIdTo: UUID,
        bankAccountIdTo: UUID,
        transactionId: UUID
    ): Event<TransferAggregate> {
        return ExternalTransferWithdrawEvent(
            accountIdFrom = accountIdFrom,
            bankAccountIdFrom = bankAccountIdFrom,
            accountIdTo = accountIdTo,
            bankAccountIdTo = bankAccountIdTo,
            transferAmount = transferAmount,
            transactionId = transactionId,
        )
    }

    fun depositMoneyTo(
        accountIdTo: UUID,
        bankAccountIdTo: UUID,
        transferAmount: BigDecimal,
        bankAccountIdFrom: UUID,
        accountIdFrom: UUID,
        transactionId: UUID
    ): Event<TransferAggregate> {
        return ExternalTransferDepositEvent(
            accountIdFrom = accountIdFrom,
            bankAccountIdFrom = bankAccountIdFrom,
            accountIdTo = accountIdTo,
            bankAccountIdTo = bankAccountIdTo,
            transferAmount = transferAmount,
            transactionId = transactionId,
        )
    }

    fun rollbackWithdrawMoney(
        accountIdFrom: UUID,
        bankAccountIdFrom: UUID,
        transferAmount: BigDecimal,
        accountIdTo: UUID,
        bankAccountIdTo: UUID,
        transactionId: UUID
    ): Event<TransferAggregate> {
        return ExternalTransferRollbackWithdrawEvent(
            accountIdFrom = accountIdFrom,
            bankAccountIdFrom = bankAccountIdFrom,
            accountIdTo = accountIdTo,
            bankAccountIdTo = bankAccountIdTo,
            transferAmount = transferAmount,
            transactionId = transactionId,
        )
    }

    fun rollbackDepositMoney(
        accountIdFrom: UUID,
        bankAccountIdFrom: UUID,
        transferAmount: BigDecimal,
        accountIdTo: UUID,
        bankAccountIdTo: UUID,
        transactionId: UUID
    ): Event<TransferAggregate> {
        return ExternalTransferRollbackDepositEvent(
            accountIdFrom = accountIdFrom,
            bankAccountIdFrom = bankAccountIdFrom,
            accountIdTo = accountIdTo,
            bankAccountIdTo = bankAccountIdTo,
            transferAmount = transferAmount,
            transactionId = transactionId,
        )
    }

    fun notifyTransferSuccess(transactionId: UUID): ExternalTransferSuccessEvent {
        return ExternalTransferSuccessEvent(transactionId)
    }

    @StateTransitionFunc
    fun withdrawMoneyFrom(event: ExternalTransferWithdrawEvent) {
        status = TransferState.PROCESSING
    }

    @StateTransitionFunc
    fun depositMoneyTo(event: ExternalTransferDepositEvent) {
        status = TransferState.PROCESSING
    }

    @StateTransitionFunc
    fun createEvent(event: TransferTransactionCreatedEvent) {
        status = TransferState.PROCESSING
    }

    @StateTransitionFunc
    fun createEvent2(event: ExternalTransferFailedEvent) {
        status = TransferState.FAILED
    }

    @StateTransitionFunc
    fun rollbackWithdrawMoney(event: ExternalTransferRollbackWithdrawEvent) {
        status = TransferState.FAILED
    }

    @StateTransitionFunc
    fun rollbackDepositMoney(event: ExternalTransferRollbackDepositEvent) {
        status = TransferState.FAILED
    }

    @StateTransitionFunc
    fun notifyTransferSuccess(event: ExternalTransferSuccessEvent) {
        status = TransferState.SUCCEEDED
    }

    @StateTransitionFunc
    fun notifyTransferSuccess(event: TrasferCreatedEvent) {
        status = TransferState.PROCESSING
    }

    @StateTransitionFunc
    fun createNewBankAccount(event: TrasferCreatedEvent) {
        transferId = event.transferId
    }

    enum class TransferState {
        CREATED,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }
}