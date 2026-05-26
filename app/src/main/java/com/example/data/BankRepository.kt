package com.example.data

import kotlinx.coroutines.flow.Flow

class BankRepository(private val bankDao: BankDao) {

    val allCards: Flow<List<CardEntity>> = bankDao.getAllCardsFlow()
    val allAccounts: Flow<List<AccountEntity>> = bankDao.getAllAccountsFlow()
    val allTransactions: Flow<List<TransactionEntity>> = bankDao.getAllTransactionsFlow()
    val allChatMessages: Flow<List<ChatMessageEntity>> = bankDao.getAllChatMessagesFlow()

    suspend fun getCardsDirect(): List<CardEntity> = bankDao.getAllCards()
    suspend fun getAccountsDirect(): List<AccountEntity> = bankDao.getAllAccounts()
    suspend fun getTransactionsDirect(): List<TransactionEntity> = bankDao.getAllTransactions()

    suspend fun addCard(card: CardEntity) {
        bankDao.insertCard(card)
    }

    suspend fun updateCard(card: CardEntity) {
        bankDao.updateCard(card)
    }

    suspend fun deleteCard(cardId: Int) {
        bankDao.deleteCard(cardId)
    }

    suspend fun addAccount(account: AccountEntity) {
        bankDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) {
        bankDao.updateAccount(account)
    }

    suspend fun deleteAccount(accountId: Int) {
        bankDao.deleteAccount(accountId)
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        bankDao.insertTransaction(transaction)
    }

    suspend fun addChatMessage(message: ChatMessageEntity) {
        bankDao.insertChatMessage(message)
    }

    suspend fun clearChatHistory() {
        bankDao.deleteAllChatMessages()
    }

    suspend fun resetDatabase() {
        bankDao.deleteAllCards()
        bankDao.deleteAllAccounts()
        bankDao.deleteAllTransactions()
        bankDao.deleteAllChatMessages()
        populateDefaultData(bankDao)
    }
}
