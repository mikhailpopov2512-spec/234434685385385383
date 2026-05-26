package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cardNumber: String,
    val cardType: String, // "MIR", "VISA", "MASTERCARD"
    val balance: Double,
    val cardName: String,
    val cardHolder: String,
    val colorHex: String, // Hex for card background visual representation
    val styleName: String // "Classic Green", "Space Mint", "Premium Dark", "Emerald Gold"
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountName: String,
    val accountNumber: String,
    val balance: Double,
    val interestRate: Double
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val senderName: String,
    val recipientName: String,
    val recipientPhone: String,
    val category: String, // "Супермаркеты", "Переводы", "Кафе и рестораны", "Транспорт", "Связь", "Другое"
    val type: String, // "TRANSFER_OUT", "TRANSFER_IN", "PAYMENT", "CASH"
    val timestamp: Long,
    val notes: String = "",
    val status: String = "SUCCESS" // "SUCCESS", "PENDING", "FAILED"
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "model" / "assistant"
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)
