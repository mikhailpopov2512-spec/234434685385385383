package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CardEntity::class, AccountEntity::class, TransactionEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BankDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao

    companion object {
        @Volatile
        private var INSTANCE: BankDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): BankDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BankDatabase::class.java,
                    "sber_bank_database"
                )
                    .addCallback(BankDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class BankDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDefaultData(database.bankDao())
                }
            }
        }
    }
}

suspend fun populateDefaultData(dao: BankDao) {
    // Check if empty, then insert default cards, accounts and transaction history
    if (dao.getAllCards().isEmpty()) {
        // Cards
        dao.insertCard(
            CardEntity(
                cardNumber = "•••• 4210",
                cardType = "MIR",
                balance = 83520.40,
                cardName = "СберКарта Мир",
                cardHolder = "МИХАИЛ ПОПОВ",
                colorHex = "#00A34B", // Sber Classic Green
                styleName = "Classic Green"
            )
        )
        dao.insertCard(
            CardEntity(
                cardNumber = "•••• 9845",
                cardType = "VISA",
                balance = 412600.00,
                cardName = "СберКарта Прайм Gold",
                cardHolder = "МИХАИЛ ПОПОВ",
                colorHex = "#B89742", // Gold / Bronze luxury
                styleName = "Emerald Gold"
            )
        )
        dao.insertCard(
            CardEntity(
                cardNumber = "•••• 7713",
                cardType = "MASTERCARD",
                balance = 12500.00,
                cardName = "Молодёжная СберКарта",
                cardHolder = "МИХАИЛ ПОПОВ",
                colorHex = "#0D1B2A", // Dark Slate Blue
                styleName = "Cyberpunk Black"
            )
        )

        // Accounts
        dao.insertAccount(
            AccountEntity(
                accountName = "Накопительный счёт",
                accountNumber = "•• 5512",
                balance = 1650300.00,
                interestRate = 16.0
            )
        )
        dao.insertAccount(
            AccountEntity(
                accountName = "Вклад Лучший %",
                accountNumber = "•• 9021",
                balance = 500000.00,
                interestRate = 18.2
            )
        )

        // Transactions
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L

        dao.insertTransaction(
            TransactionEntity(
                amount = 2340.50,
                senderName = "СберКарта •••• 4210",
                recipientName = "Супермаркет 'Азбука Вкуса'",
                recipientPhone = "",
                category = "Супермаркеты",
                type = "PAYMENT",
                timestamp = now - (0.1 * dayInMs).toLong()
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 15000.00,
                senderName = "Анна С.",
                recipientName = "Михаил П.",
                recipientPhone = "+79113334455",
                category = "Переводы",
                type = "TRANSFER_IN",
                timestamp = now - (0.4 * dayInMs).toLong(),
                notes = "Возврат долга за ужин"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 890.00,
                senderName = "СберКарта •••• 4210",
                recipientName = "Кофейня 'DoubleB'",
                recipientPhone = "",
                category = "Кафе и рестораны",
                type = "PAYMENT",
                timestamp = now - (1.2 * dayInMs).toLong()
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 400.00,
                senderName = "СберКарта •••• 7713",
                recipientName = "Пополнение баланса МТС",
                recipientPhone = "+79159998822",
                category = "Связь",
                type = "PAYMENT",
                timestamp = now - (1.8 * dayInMs).toLong()
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 3500.00,
                senderName = "СберКарта •••• 9845",
                recipientName = "Перевод Александру В.",
                recipientPhone = "+79051112233",
                category = "Переводы",
                type = "TRANSFER_OUT",
                timestamp = now - (2.5 * dayInMs).toLong(),
                notes = "Подарок на день рождения!"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 6120.00,
                senderName = "СберКарта •••• 9845",
                recipientName = "ЖКУ Москва Квартплата",
                recipientPhone = "",
                category = "Услуги ЖКХ",
                type = "PAYMENT",
                timestamp = now - (3.1 * dayInMs).toLong()
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                amount = 450.00,
                senderName = "СберКарта •••• 4210",
                recipientName = "Яндекс Такси",
                recipientPhone = "",
                category = "Транспорт",
                type = "PAYMENT",
                timestamp = now - (4.0 * dayInMs).toLong()
            )
        )

        // Initial Chat Assistant welcome message
        dao.insertChatMessage(
            ChatMessageEntity(
                role = "assistant",
                messageText = "Привет, Михаил! Я твой финансовый ассистент Салют. Чем могу тебе помочь? Я могу проанализировать твои расходы, подсказать баланс по картам или рассказать о выгодных вкладах!",
                timestamp = now - 1000
            )
        )
    }
}
