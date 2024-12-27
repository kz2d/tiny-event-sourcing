package ru.quipy.bankDemo.accounts.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.quipy.core.EventSourcingService
import ru.quipy.saga.SagaManager
import ru.quipy.bankDemo.accounts.api.AccountAggregate
import ru.quipy.bankDemo.accounts.api.AccountCreatedEvent
import ru.quipy.bankDemo.accounts.api.BankAccountCreatedEvent
import ru.quipy.bankDemo.accounts.api.ExternalAccountTransferEvent
import ru.quipy.bankDemo.accounts.logic.Account
import ru.quipy.bankDemo.accounts.logic.BankAccount
import java.math.BigDecimal
import java.util.*

@RestController
@RequestMapping("/accounts")
class AccountController(
    val accountEsService: EventSourcingService<UUID, AccountAggregate, Account>,
    val sagaManager: SagaManager
) {

    @PostMapping("/{holderId}")
    fun createAccount(@PathVariable holderId: UUID): AccountCreatedEvent {
        return accountEsService.create { it.createNewAccount(holderId = holderId) }
    }

    @GetMapping("/{accountId}")
    fun getAccount(@PathVariable accountId: UUID): Account? {
        return accountEsService.getState(accountId)
    }

    @PostMapping("/{accountId}/bankAccount")
    fun createBankAccount(@PathVariable accountId: UUID): BankAccountCreatedEvent {
        return accountEsService.update(accountId) { it.createNewBankAccount() }
    }

    @PostMapping("/{accountId}/bankAccount/{bankAccountId}/topup/{amount}")
    fun deposit(@PathVariable accountId: UUID, @PathVariable bankAccountId: UUID, amount: BigDecimal) {
        accountEsService.update(accountId)
        {

//           it.deposit(bankAccountId, BigDecimal(10_000))

            it.depositNice(bankAccountId, amount)
//            it.createNewAccount(holderId = UUID.randomUUID())
        }

        println(accountEsService.getState(accountId)?.bankAccounts?.get(bankAccountId))
    }

    @GetMapping("/{accountId}/bankAccount/{bankAccountId}")
    fun getBankAccount(@PathVariable accountId: UUID, @PathVariable bankAccountId: UUID): BankAccount? {
        return accountEsService.getState(accountId)?.bankAccounts?.get(bankAccountId)
    }

    @GetMapping("/{accountId}/bankAccount/{bankAccountId}/to/{toAccountId}/bankAccount/{toBankAccountId}/{amount}")
    fun transfer(
        @PathVariable accountId: UUID,
        @PathVariable bankAccountId: UUID,
        @PathVariable toAccountId: UUID,
        @PathVariable toBankAccountId: UUID,
        @PathVariable amount: BigDecimal,
    ): ExternalAccountTransferEvent {
        val sagaContext = sagaManager
            .launchSaga("TRANSFER", "start transfer money")
            .sagaContext()
        accountEsService.getState(accountId)?.bankAccounts?.get(bankAccountId) ?: println("user not exist")
        accountEsService.getState(toAccountId)?.bankAccounts?.get(toBankAccountId) ?: println("user 2not exist")

        println("lol")
        return accountEsService.update(accountId, sagaContext) {
            it.startTransfer(
                accountId,
                bankAccountId,
                toAccountId,
                toBankAccountId,
                amount
            )
        }
    }
}