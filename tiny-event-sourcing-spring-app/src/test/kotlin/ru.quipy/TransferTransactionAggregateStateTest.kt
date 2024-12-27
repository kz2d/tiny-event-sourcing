package ru.quipy

import org.awaitility.Awaitility
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import ru.quipy.bankDemo.accounts.api.AccountAggregate
import ru.quipy.bankDemo.accounts.logic.Account
import ru.quipy.bankDemo.transfers.api.TransferAggregate
import ru.quipy.bankDemo.transfers.logic.Transfer
import ru.quipy.config.DockerPostgresDataSourceInitializer
import ru.quipy.core.EventSourcingService
import java.math.BigDecimal
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(
    initializers = [DockerPostgresDataSourceInitializer::class])
@EnableAutoConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransferTransactionAggregateStateTest: BaseTest(testId) {
    companion object {
        private val testAccountId = UUID.fromString("b88f83bf-9a2a-4091-9cb3-3185f6f65a4b")
        private val testAccount2Id = UUID.fromString("1fccc03e-4ed3-47b7-8f76-8e62efb5e36e")
        private val userId = UUID.fromString("330f9c97-4031-4bd4-ab49-a347719ace25")
        private const val testId = "TransferTransactionAggregateStateTest"
    }

    @Autowired
    private lateinit var accountEsService: EventSourcingService<UUID, AccountAggregate, Account>

    @Autowired
    private lateinit var transactionEsService: EventSourcingService<UUID, TransferAggregate, Transfer>


    @BeforeEach
    fun init() {
        cleanDatabase()
    }

    override fun cleanDatabase() {
        super.cleanDatabase()
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").`is`(testAccountId)), "accounts")
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").`is`(testAccount2Id)), "accounts")
        mongoTemplate.remove(Query.query(Criteria.where("_id").`is`(testAccountId)), "snapshots")
        mongoTemplate.remove(Query.query(Criteria.where("_id").`is`(testAccount2Id)), "snapshots")
        mongoTemplate.remove(Query(), "transfers")
    }

    @Test
    fun createTwoBankAccountsDepositAndTransfer() {
        val account1 = accountEsService.create {
            it.createNewAccount(id = testAccountId, holderId = userId)
        }


        // first create and deposit
        val createdBankAccountEvent1 = accountEsService.update(testAccountId) {
            it.createNewBankAccount()
        }

        val depositAmount = BigDecimal(100.0)
        accountEsService.update(testAccountId) {
            it.depositNice(createdBankAccountEvent1.bankAccountId, depositAmount)
        }

        val account2 = accountEsService.create {
            it.createNewAccount(id = testAccount2Id, holderId = userId)
        }

        // second create
        val createdBankAccountEvent2 = accountEsService.update(testAccount2Id) {
            it.createNewBankAccount()
        }

        Awaitility.await().atLeast(Duration.ofSeconds(10))

        // transfer
        val id = UUID.randomUUID()
        transactionEsService.create{
            it.createNew(id)
        };

        val trans = transactionEsService.update(id){
            it.withdrawMoneyFrom(
                accountIdFrom = account1.accountId,
                bankAccountIdFrom = createdBankAccountEvent1.bankAccountId,
                accountIdTo = account2.accountId,
                bankAccountIdTo = createdBankAccountEvent2.bankAccountId,
                transactionId = id,
                transferAmount = BigDecimal(100)
            )
        }

        Awaitility.await().atLeast(Duration.ofSeconds(10))

        val state1 = accountEsService.getState(testAccountId)!!
        val state2 = accountEsService.getState(testAccount2Id)!!

        state1.bankAccounts[createdBankAccountEvent1.bankAccountId]!!.balance == BigDecimal.ZERO &&
                state2.bankAccounts[createdBankAccountEvent2.bankAccountId]!!.balance == BigDecimal(100)
    }

    @Test
    fun rolback() {
        val account1 = accountEsService.create {
            it.createNewAccount(id = testAccountId, holderId = userId)
        }


        // first create and deposit
        val createdBankAccountEvent1 = accountEsService.update(testAccountId) {
            it.createNewBankAccount()
        }

        val depositAmount = BigDecimal(100.0)
        accountEsService.update(testAccountId) {
            it.depositNice(createdBankAccountEvent1.bankAccountId, depositAmount)
        }

        val account2 = accountEsService.create {
            it.createNewAccount(id = testAccount2Id, holderId = userId)
        }

        // second create
        val createdBankAccountEvent2 = accountEsService.update(testAccount2Id) {
            it.createNewBankAccount()
        }

        accountEsService.update(testAccount2Id) {
            it.depositNice(createdBankAccountEvent2.bankAccountId, BigDecimal(9_999_999))
        }

        Awaitility.await().atLeast(Duration.ofSeconds(10))

        // transfer
        val id = UUID.randomUUID()
        transactionEsService.create{
            it.createNew(id)
        };

        val trans = transactionEsService.update(id){
            it.withdrawMoneyFrom(
                accountIdFrom = account1.accountId,
                bankAccountIdFrom = createdBankAccountEvent1.bankAccountId,
                accountIdTo = account2.accountId,
                bankAccountIdTo = createdBankAccountEvent2.bankAccountId,
                transactionId = id,
                transferAmount = BigDecimal(100)
            )
        }

        Awaitility.await().atLeast(Duration.ofSeconds(10))

        val state1 = accountEsService.getState(testAccountId)!!
        val state2 = accountEsService.getState(testAccount2Id)!!

        state1.bankAccounts[createdBankAccountEvent1.bankAccountId]!!.balance == BigDecimal.ZERO &&
                state2.bankAccounts[createdBankAccountEvent2.bankAccountId]!!.balance == BigDecimal(100)
    }


    @Test
        fun testCreateAccountAndBankAccount() {
            // Create account
            val account1 = accountEsService.create {
                it.createNewAccount(id = testAccountId, holderId = userId)
            }
            // Create bank account
            val bankAccountEvent1 = accountEsService.update(testAccountId) {
                it.createNewBankAccount()
            }
            // Verify
            Assertions.assertEquals(testAccountId, account1.accountId)
            Assertions.assertEquals(1, accountEsService.getState(testAccountId)!!.bankAccounts.size)
            println("Test 1: Account and Bank Account created successfully.")
        }

        @Test
        fun testDepositIntoAccount() {
            val testAccountId = UUID.randomUUID()
            val testAccount2Id = UUID.randomUUID()
            // Create accounts and bank accounts
            val account1 = accountEsService.create {
                it.createNewAccount(id = testAccountId, holderId = userId)
            }
            val account2 = accountEsService.create {
                it.createNewAccount(id = testAccount2Id, holderId = userId)
            }
            // Create bank account
            val bankAccountEvent1 = accountEsService.update(testAccountId) {
                it.createNewBankAccount()
            }
            // Deposit money
            val depositAmount = BigDecimal("100.0")
            accountEsService.update(testAccountId) {
                it.depositNice(bankAccountEvent1.bankAccountId, depositAmount)
            }
            // Verify
            Assertions.assertEquals(
                depositAmount,
                accountEsService.getState(testAccountId)!!.bankAccounts[bankAccountEvent1.bankAccountId]!!.balance
            )
            println("Test 2: Deposit into account successful.")
        }

        @Test
        fun testConcurrentDeposits() {

            val testAccountId = UUID.randomUUID()
            val testAccount2Id = UUID.randomUUID()
            val account1 = accountEsService.create {
                it.createNewAccount(id = testAccountId, holderId = userId)
            }

            val account2 = accountEsService.create {
                it.createNewAccount(id = testAccount2Id, holderId = userId)
            }
            // Create bank account
            val bankAccountEvent1 = accountEsService.update(testAccountId) {
                it.createNewBankAccount()
            }
            // Perform concurrent deposits
            val depositAmountConcurrent = BigDecimal("10.0")
            val threads = List(10) {
                thread {
                    accountEsService.update(testAccountId) {
                        it.depositNice(bankAccountEvent1.bankAccountId, depositAmountConcurrent)
                    }
                }
            }
            threads.forEach { it.join() }
            // Verify balance
            Assertions.assertEquals(
                BigDecimal("100.0"),
                accountEsService.getState(testAccountId)!!.bankAccounts[bankAccountEvent1.bankAccountId]!!.balance
            )
            println("Test 6: Concurrent deposits handled correctly.")
        }


    private fun existsAccountState(aggregateId: UUID, predicate: (Account, Long) -> Boolean): Boolean {
        var version = 1L
        var state = accountEsService.getStateOfVersion(aggregateId, version)
        while (state != null) {
            if (predicate.invoke(state, version)) return true
            version++
            state = accountEsService.getStateOfVersion(aggregateId, version)
        }
        return false
    }
}