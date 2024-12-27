package ru.quipy

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import ru.quipy.bankDemo.accounts.api.AccountAggregate
import ru.quipy.bankDemo.accounts.logic.Account
import ru.quipy.config.DockerPostgresDataSourceInitializer
import ru.quipy.core.EventSourcingService
import java.math.BigDecimal
import java.util.*

@SpringBootTest
@ContextConfiguration(
    initializers = [DockerPostgresDataSourceInitializer::class])
@EnableAutoConfiguration
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BankAggregateStateTest: BaseTest(testId.toString()) {
    companion object {
        private val testId = UUID.randomUUID()
        private val userId = UUID.randomUUID()
    }

    @Autowired
    private lateinit var bankESService: EventSourcingService<UUID, AccountAggregate, Account>

    @BeforeEach
    fun init() {
        cleanDatabase()
    }

    @Test
    fun createAccount() {
        bankESService.create {
            it.createNewAccount(id = testId, holderId = userId)
        }

        val state = bankESService.getState(testId)!!

        Assertions.assertEquals(testId, state.getId())
    }

    @Test
    fun createBankAccount() {
        bankESService.create {
            it.createNewAccount(id = testId, holderId = userId)
        }
        val createdEvent = bankESService.update(testId) {
            it.createNewBankAccount()
        }

        val state = bankESService.getState(testId)!!

        Assertions.assertEquals(testId, state.getId())
        Assertions.assertEquals(1, state.bankAccounts.size)
        Assertions.assertNotNull(state.bankAccounts[createdEvent.bankAccountId])
        Assertions.assertEquals(createdEvent.bankAccountId, state.bankAccounts[createdEvent.bankAccountId]!!.id)
        Assertions.assertEquals(BigDecimal.ZERO, state.bankAccounts[createdEvent.bankAccountId]!!.balance)
    }

    @Test
    fun createBankAccountAndDeposit() {
        bankESService.create {
            it.createNewAccount(id = testId, holderId = userId)
        }

        val createdEvent = bankESService.update(testId) {
            it.createNewBankAccount()
        }

        val depositAmount = BigDecimal(100.0)


        val state = bankESService.getState(testId)!!

        Assertions.assertEquals(testId, state.getId())
        Assertions.assertEquals(1, state.bankAccounts.size)
        Assertions.assertNotNull(state.bankAccounts[createdEvent.bankAccountId])
        Assertions.assertEquals(createdEvent.bankAccountId, state.bankAccounts[createdEvent.bankAccountId]!!.id)

    }

    @Test
    fun createTwoBankAccounts() {
        bankESService.create {
            it.createNewAccount(id = testId, holderId = userId)
        }

        val createdBankAccountEvent1 = bankESService.update(testId) {
            it.createNewBankAccount()
        }

        val createdBankAccountEvent2 = bankESService.update(testId) {
            it.createNewBankAccount()
        }

        val state = bankESService.getState(testId)!!

        Assertions.assertEquals(testId, state.getId())
        Assertions.assertEquals(2, state.bankAccounts.size)
        // first
        Assertions.assertNotNull(state.bankAccounts[createdBankAccountEvent1.bankAccountId])
        Assertions.assertEquals(
            state.bankAccounts[createdBankAccountEvent1.bankAccountId]!!.id,
            createdBankAccountEvent1.bankAccountId
        )
        Assertions.assertEquals(BigDecimal.ZERO, state.bankAccounts[createdBankAccountEvent1.bankAccountId]!!.balance)
        // second
        Assertions.assertNotNull(state.bankAccounts[createdBankAccountEvent2.bankAccountId])
        Assertions.assertEquals(
            createdBankAccountEvent2.bankAccountId,
            state.bankAccounts[createdBankAccountEvent2.bankAccountId]!!.id
        )
        Assertions.assertEquals(BigDecimal.ZERO, state.bankAccounts[createdBankAccountEvent2.bankAccountId]!!.balance)
    }
}