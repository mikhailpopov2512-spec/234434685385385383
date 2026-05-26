package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BankViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BankDatabase.getDatabase(application, viewModelScope)
    val repository = BankRepository(database.bankDao())

    private val sharedPrefs = application.getSharedPreferences("sber_prefs", Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "Михаил Попов") ?: "Михаил Попов")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userPhone = MutableStateFlow(sharedPrefs.getString("user_phone", "+7 (911) 333-44-55") ?: "+7 (911) 333-44-55")
    val userPhone: StateFlow<String> = _userPhone.asStateFlow()

    fun updateProfile(newName: String, newPhone: String) {
        _userName.value = newName
        _userPhone.value = newPhone
        sharedPrefs.edit()
            .putString("user_name", newName)
            .putString("user_phone", newPhone)
            .apply()
    }

    // --- State Streams ---
    val cards: StateFlow<List<CardEntity>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allChatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI UI-specific States ---
    private val _isAssistantLoading = MutableStateFlow(false)
    val isAssistantLoading: StateFlow<Boolean> = _isAssistantLoading.asStateFlow()

    private val _transferStatus = MutableStateFlow<TransferProgress>(TransferProgress.Idle)
    val transferStatus: StateFlow<TransferProgress> = _transferStatus.asStateFlow()

    private val _activeCardDesign = MutableStateFlow<CardEntity?>(null)
    val activeCardDesign: StateFlow<CardEntity?> = _activeCardDesign.asStateFlow()

    init {
        // Pre-populate DB if needed is run implicitly via Room Callback
    }

    fun selectCardForDesign(card: CardEntity?) {
        _activeCardDesign.value = card
    }

    // --- Card Skin Update ---
    fun updateCardSkin(cardId: Int, newColorHex: String, newStyleName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = cards.value
            val targetCard = list.firstOrNull { it.id == cardId }
            if (targetCard != null) {
                val updated = targetCard.copy(colorHex = newColorHex, styleName = newStyleName)
                repository.updateCard(updated)
            }
        }
    }

    // --- Create Custom Card ---
    fun createNewCard(cardName: String, styleName: String, colorHex: String, cardType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val randomNum = (1000..9999).random()
            val newCard = CardEntity(
                cardNumber = "•••• $randomNum",
                cardType = cardType.uppercase(),
                balance = 5000.00, // starting gift balance
                cardName = cardName,
                cardHolder = "МИХАИЛ ПОПОВ",
                colorHex = colorHex,
                styleName = styleName
            )
            repository.addCard(newCard)
        }
    }

    // --- Simulated Interactive Transfer Process ---
    fun performTransfer(
        sourceCardId: Int,
        recipientName: String,
        recipientPhone: String,
        amount: Double,
        transferNotes: String
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _transferStatus.value = TransferProgress.Error("Сумма перевода должна быть больше нуля.")
                return@launch
            }

            _transferStatus.value = TransferProgress.Processing

            // Delay for immersive banking visual process
            delay(1800)

            withContext(Dispatchers.IO) {
                val currentCards = cards.value
                val sourceCard = currentCards.firstOrNull { it.id == sourceCardId }

                if (sourceCard == null) {
                    _transferStatus.value = TransferProgress.Error("Выбранная карта не найдена.")
                    return@withContext
                }

                if (sourceCard.balance < amount) {
                    _transferStatus.value = TransferProgress.Error("Недостаточно средств. Доступно: ${formatCurrency(sourceCard.balance)}")
                    return@withContext
                }

                // Deduct from sender card
                val updatedCard = sourceCard.copy(balance = sourceCard.balance - amount)
                repository.updateCard(updatedCard)

                // Log Transaction
                val transaction = TransactionEntity(
                    amount = amount,
                    senderName = "${sourceCard.cardName} ${sourceCard.cardNumber}",
                    recipientName = recipientName,
                    recipientPhone = recipientPhone,
                    category = "Переводы",
                    type = "TRANSFER_OUT",
                    timestamp = System.currentTimeMillis(),
                    notes = transferNotes,
                    status = "SUCCESS"
                )
                repository.addTransaction(transaction)

                // Trigger a pleasant success screen
                _transferStatus.value = TransferProgress.Success(transaction)
            }
        }
    }

    // --- Cell Recharge or Utilities Payment ---
    fun performPayment(
        sourceCardId: Int,
        providerName: String,
        phoneNumber: String,
        amount: Double,
        category: String
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _transferStatus.value = TransferProgress.Error("Сумма платежа должна быть больше нуля.")
                return@launch
            }

            _transferStatus.value = TransferProgress.Processing
            delay(1500)

            withContext(Dispatchers.IO) {
                val currentCards = cards.value
                val sourceCard = currentCards.firstOrNull { it.id == sourceCardId }

                if (sourceCard == null) {
                    _transferStatus.value = TransferProgress.Error("Карта списания не найдена.")
                    return@withContext
                }

                if (sourceCard.balance < amount) {
                    _transferStatus.value = TransferProgress.Error("Недостаточно средств для совершения платежа.")
                    return@withContext
                }

                // Deduct
                val updatedCard = sourceCard.copy(balance = sourceCard.balance - amount)
                repository.updateCard(updatedCard)

                // Log
                val transaction = TransactionEntity(
                    amount = amount,
                    senderName = "${sourceCard.cardName} ${sourceCard.cardNumber}",
                    recipientName = providerName,
                    recipientPhone = phoneNumber,
                    category = category,
                    type = "PAYMENT",
                    timestamp = System.currentTimeMillis(),
                    notes = "Оплата услуг",
                    status = "SUCCESS"
                )
                repository.addTransaction(transaction)

                _transferStatus.value = TransferProgress.Success(transaction)
            }
        }
    }

    fun clearTransferStatus() {
        _transferStatus.value = TransferProgress.Idle
    }

    // --- Send Assistant Query To Gemini 3.5 Flash ---
    fun sendMessageToAssistant(userText: String) {
        if (userText.isBlank()) return

        val userMessage = ChatMessageEntity(role = "user", messageText = userText)
        viewModelScope.launch {
            repository.addChatMessage(userMessage)
            _isAssistantLoading.value = true

            // Formulate current financial status prompt to make Gemini 100% smart and contextualized
            val cardInfoSummary = cards.value.joinToString("\n") {
                "- Карта: ${it.cardName} (${it.cardNumber}), Баланс: ${formatCurrency(it.balance)}, Дизайн: ${it.styleName}"
            }
            val accountInfoSummary = accounts.value.joinToString("\n") {
                "- Счёт: ${it.accountName} (${it.accountNumber}), Баланс: ${formatCurrency(it.balance)}, Ставка: ${it.interestRate}% годовых"
            }
            val lastTransactions = transactions.value.take(4).joinToString("\n") {
                "- ${if (it.type == "TRANSFER_OUT" || it.type == "PAYMENT") "Расход" else "Доход"} на сумму ${formatCurrency(it.amount)} в категории '${it.category}' -> Кому/Где: ${it.recipientName}"
            }

            val systemInstructionText = """
                Ты — Салют, умный, отзывчивый, вежливый и технологичный финансовый ассистент СберБанка. Твоя миссия — помогать пользователю (Михаил) управлять своими финансами, советовать выгодные вложения и анализировать его бюджет.
                Общайся вежливо, профессионально и дружелюбно на русском языке. Ответы должны быть ёмкими, полезными, структурированными в стиле Сбера. Избегай длинной воды.
                
                Тебе полностью известна финансовая сводка пользователя на данный момент:
                КАРТЫ пользователя:
                $cardInfoSummary
                
                СБЕРЕЖЕНИЯ И СЧЕТА пользователя:
                $accountInfoSummary
                
                ПОСЛЕДНИЕ ТРАНЗАКЦИИ:
                $lastTransactions
                
                Будь готов ответить на вопросы по балансу конкретных карт, проанализировать расходы по категориям, подсказать по ставкам вкладов.
                Если пользователь хочет перевести деньги, вежливо подскажи ему перейти на вкладку 'Платежи'.
                Придумай короткое, но запоминающееся финансовое пожелание или совет, если это уместно. Применяй эмодзи сдержанно для красоты.
            """.trimIndent()

            // Construct full chat log history for context
            val history = chatMessages.value.map {
                Content(parts = listOf(Part(text = it.messageText)))
            }

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return immediate smart local offline response if no API key is provided
                delay(1200)
                val responseText = "Привет, Михаил! Извините, но в системе отсутствует настроенный API-ключ Gemini. \n\nТем не менее, я вижу ваши финансы в реальном времени! Баланс вашей основной СберКарты составляет ${
                    cards.value.firstOrNull()?.let { formatCurrency(it.balance) } ?: "0 ₽"
                }. Могу настроить вам новый дизайн картинок или помочь сделать переводы на вкладке рядом!"
                repository.addChatMessage(ChatMessageEntity(role = "model", messageText = responseText))
                _isAssistantLoading.value = false
                return@launch
            }

            try {
                // Compile the messages request
                val requestBody = GenerateContentRequest(
                    contents = history,
                    generationConfig = GenerationConfig(temperature = 0.7f),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
                )

                val response = GeminiClient.service.generateContent(apiKey, requestBody)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Извините, сейчас я не могу ответить. Пожалуйста, попробуйте позже."

                repository.addChatMessage(ChatMessageEntity(role = "model", messageText = responseText))
            } catch (e: Exception) {
                Log.e("SberAssistant", "Error calling Gemini API", e)
                val errorMessage = "Ассистент временно недоступен: ${e.localizedMessage}. Однако я всегда на связи за чашкой кофе!"
                repository.addChatMessage(ChatMessageEntity(role = "model", messageText = errorMessage))
            } finally {
                _isAssistantLoading.value = false
            }
        }
    }

    // --- Reset All Database Data ---
    fun resetAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetDatabase()
        }
    }

    // --- Admin Operations ---
    fun adminUpdateCardBalance(cardId: Int, newBalance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val cardList = cards.value
            val target = cardList.firstOrNull { it.id == cardId }
            if (target != null) {
                repository.updateCard(target.copy(balance = newBalance))
            }
        }
    }

    fun adminDeleteCard(cardId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCard(cardId)
        }
    }

    fun adminUpdateAccountBalance(accountId: Int, newBalance: Double, newRate: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val accountList = accounts.value
            val target = accountList.firstOrNull { it.id == accountId }
            if (target != null) {
                repository.updateAccount(target.copy(balance = newBalance, interestRate = newRate))
            }
        }
    }

    fun adminDeleteAccount(accountId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAccount(accountId)
        }
    }

    fun adminAddAccount(name: String, balance: Double, rate: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val randomNum = (1000..9999).random()
            val newAccount = AccountEntity(
                accountName = name,
                accountNumber = "•• $randomNum",
                balance = balance,
                interestRate = rate
            )
            repository.addAccount(newAccount)
        }
    }

    fun adminAddIncomingTransaction(
        senderName: String,
        amount: Double,
        category: String,
        recipientCardId: Int,
        notes: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cardList = cards.value
            val targetCard = cardList.firstOrNull { it.id == recipientCardId }
            if (targetCard != null) {
                // Update card balance
                repository.updateCard(targetCard.copy(balance = targetCard.balance + amount))

                // Insert dynamic incoming transaction
                val transaction = TransactionEntity(
                    amount = amount,
                    senderName = senderName,
                    recipientName = userName.value,
                    recipientPhone = userPhone.value,
                    category = category,
                    type = "TRANSFER_IN",
                    timestamp = System.currentTimeMillis(),
                    notes = notes,
                    status = "SUCCESS"
                )
                repository.addTransaction(transaction)
            }
        }
    }

    // --- Currency Formatter ---
    fun formatCurrency(amount: Double): String {
        val formatter = java.text.DecimalFormat("#,##0.00")
        return "${formatter.format(amount).replace(".", ",").replace(",00", "")} ₽"
    }
}

// Sealed visual progress representations
sealed interface TransferProgress {
    object Idle : TransferProgress
    object Processing : TransferProgress
    data class Success(val transaction: TransactionEntity) : TransferProgress
    data class Error(val message: String) : TransferProgress
}
