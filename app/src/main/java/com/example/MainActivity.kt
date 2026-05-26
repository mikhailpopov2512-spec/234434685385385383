package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AccountEntity
import com.example.data.CardEntity
import com.example.data.ChatMessageEntity
import com.example.data.TransactionEntity
import com.example.ui.BankViewModel
import com.example.ui.TransferProgress
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// Compact data holder for top slide-down simulation messages
data class MockNotification(
    val title: String,
    val message: String,
    val amount: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppEntry()
            }
        }
    }
}

@Composable
fun MainAppEntry() {
    val viewModel: BankViewModel = viewModel()
    
    // Core database flows from View Model
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPhone by viewModel.userPhone.collectAsStateWithLifecycle()
    
    // Auth and administrative navigation
    var isAuthorized by remember { mutableStateOf(false) }
    var pinValue by remember { mutableStateOf("") }
    
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var adminPasscodeInput by remember { mutableStateOf("") }
    var adminPassError by remember { mutableStateOf("") }
    var isAdminLoggedIn by remember { mutableStateOf(false) }
    
    // Bottom client tabs
    var selectedClientTab by remember { mutableStateOf("home") } // "home", "payments", "saljut", "profile"
    
    // Dynamic overlay popups
    var inspectedTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var receivedNotificationAlert by remember { mutableStateOf<MockNotification?>(null) }
    
    // Notification auto dismiss handler after 4s
    LaunchedEffect(receivedNotificationAlert) {
        if (receivedNotificationAlert != null) {
            delay(4000)
            receivedNotificationAlert = null
        }
    }
    
    // Monitor incoming payment transactions to display a gorgeous notification banner
    var transactionHistorySizeAtLastCheck by remember { mutableStateOf(-1) }
    LaunchedEffect(transactions) {
        if (transactionHistorySizeAtLastCheck == -1) {
            transactionHistorySizeAtLastCheck = transactions.size
            return@LaunchedEffect
        }
        if (transactions.size > transactionHistorySizeAtLastCheck) {
            val lastCreatedTx = transactions.firstOrNull()
            transactionHistorySizeAtLastCheck = transactions.size
            if (lastCreatedTx != null && lastCreatedTx.type == "TRANSFER_IN") {
                receivedNotificationAlert = MockNotification(
                    title = "Зачисление средств",
                    message = "Вам зачислено +${viewModel.formatCurrency(lastCreatedTx.amount)} от ${lastCreatedTx.senderName}",
                    amount = lastCreatedTx.amount
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            // Render screens: Admin workspace VS Client Workspace VS Screen Lock PIN
            when {
                isAdminLoggedIn -> {
                    AdminDashboardScreen(
                        userName = userName,
                        userPhone = userPhone,
                        cards = cards,
                        accounts = accounts,
                        viewModel = viewModel,
                        onCloseAdmin = {
                            isAdminLoggedIn = false
                            isAuthorized = false
                            pinValue = ""
                        }
                    )
                }
                
                !isAuthorized -> {
                    SberLoginScreen(
                        userName = userName,
                        pinValue = pinValue,
                        onNumClick = { digit ->
                            if (pinValue.length < 5) {
                                pinValue += digit
                                if (pinValue.length == 5) {
                                    isAuthorized = true
                                }
                            }
                        },
                        onBackspace = {
                            if (pinValue.isNotEmpty()) {
                                pinValue = pinValue.dropLast(1)
                            }
                        },
                        onBiometricAuth = {
                            isAuthorized = true
                        },
                        onAdminPanelTouch = {
                            showAdminLoginDialog = true
                            adminPasscodeInput = ""
                            adminPassError = ""
                        }
                    )
                }
                
                else -> {
                    // Logged in user workspace
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            MainTabController(
                                tab = selectedClientTab,
                                userName = userName,
                                userPhone = userPhone,
                                cards = cards,
                                accounts = accounts,
                                transactions = transactions,
                                chatMessages = chatMessages,
                                viewModel = viewModel,
                                onTxSelect = { inspectedTx = it },
                                onSignOutClient = {
                                    isAuthorized = false
                                    pinValue = ""
                                    selectedClientTab = "home"
                                }
                            )
                        }
                        
                        // Custom Navigation
                        SberNavigationBar(
                            selectedTab = selectedClientTab,
                            onSelect = { selectedClientTab = it }
                        )
                    }
                }
            }
            
            // Slide-down push banner
            AnimatedVisibility(
                visible = receivedNotificationAlert != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .zIndex(99f)
            ) {
                receivedNotificationAlert?.let { ntf ->
                    ToastIncomingNotification(
                        ntf = ntf,
                        onDismiss = { receivedNotificationAlert = null }
                    )
                }
            }
            
            // Receipt view popup
            inspectedTx?.let { tx ->
                TransactionDetailsDialog(
                    tx = tx,
                    onDismiss = { inspectedTx = null },
                    formatCurrency = { viewModel.formatCurrency(it) }
                )
            }
            
            // Password prompt modal for managers/administrators (PIN 0000)
            if (showAdminLoginDialog) {
                AlertDialog(
                    onDismissRequest = { showAdminLoginDialog = false },
                    title = {
                        Text(
                            text = "Код доступа администратора",
                            fontWeight = FontWeight.Bold,
                            color = SberOnSurfaceDark,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                "Пожалуйста, подтвердите права сотрудника Сбера. Пароль по умолчанию: 0000",
                                color = SberMutedText,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = adminPasscodeInput,
                                onValueChange = {
                                    if (it.length <= 4) {
                                        adminPasscodeInput = it
                                        adminPassError = ""
                                    }
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                label = { Text("Пароль доступа") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = SberOnSurfaceDark,
                                    unfocusedTextColor = SberOnSurfaceDark,
                                    focusedContainerColor = SberSurfaceDark,
                                    unfocusedContainerColor = SberSurfaceDark,
                                    focusedIndicatorColor = SberGreenPrimary,
                                    unfocusedIndicatorColor = Color.DarkGray
                                )
                            )
                            if (adminPassError.isNotEmpty()) {
                                Text(
                                    text = adminPassError,
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (adminPasscodeInput == "0000") {
                                    showAdminLoginDialog = false
                                    isAdminLoggedIn = true
                                } else {
                                    adminPassError = "Пароль не совпадает. Введите '0000'"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary)
                        ) {
                            Text("Подтвердить", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdminLoginDialog = false }) {
                            Text("Отмена", color = SberMutedText)
                        }
                    },
                    containerColor = SberSurfaceDark,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

// ==========================================
// 1. LOGIN PIN PAD REPRESENTATION
// ==========================================
@Composable
fun SberLoginScreen(
    userName: String,
    pinValue: String,
    onNumClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometricAuth: () -> Unit,
    onAdminPanelTouch: () -> Unit
) {
    val givenName = userName.split(" ").firstOrNull() ?: userName
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF071F11), Color(0xFF030D07))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Logo & Design head
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1BE36E), Color(0xFF007A33))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalance,
                    contentDescription = "Sber Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "СБЕРБАНК",
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )
            Text(
                text = "ОНЛАЙН",
                letterSpacing = 3.sp,
                fontSize = 11.sp,
                color = SberGreenBright,
                fontWeight = FontWeight.Bold
            )
        }

        // Prompt & Custom round markers
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Рады видеть вас, $givenName!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = SberOnSurfaceDark
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Введите 5-значный пин-код",
                fontSize = 13.sp,
                color = SberMutedText
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dot markers Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { dotIdx ->
                    val filled = dotIdx < pinValue.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = if (filled) SberGreenBright else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .border(
                                width = if (filled) 0.dp else 1.dp,
                                color = Color.White.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        // PIN numerical keypad columns (48dp Touch Targets compliant)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val keyrows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            
            keyrows.forEach { rowVals ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    rowVals.forEach { numVal ->
                        PinKeyButton(
                            text = numVal,
                            onClick = { onNumClick(numVal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Bottom bar with Fingerprint, Zero and backspace
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                IconButton(
                    onClick = onBiometricAuth,
                    modifier = Modifier
                        .weight(1f)
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Быстрый отпечаток",
                        tint = SberGreenBright,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                PinKeyButton(
                    text = "0",
                    onClick = { onNumClick("0") },
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier
                        .weight(1f)
                        .size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Стереть символ",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            // Admin Panel touch replica
            Button(
                onClick = onAdminPanelTouch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = SberMutedText
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Админ пульт",
                    tint = SberGreenBright,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ВХОД ДЛЯ АДМИНИСТРАТОРА",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
        }
    }
}

// Custom Key Button
@Composable
fun PinKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .background(Color.White.copy(alpha = 0.04f), shape = CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape = CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

// ==========================================
// 2. DETAILED NAVIGATION BAR (M3 COMPLIANT)
// ==========================================
@Composable
fun SberNavigationBar(
    selectedTab: String,
    onSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = SberSurfaceDark,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding(),
        windowInsets = WindowInsets.navigationBars
    ) {
        val menuItems = listOf(
            Triple("home", "Главная", Icons.Rounded.Home),
            Triple("payments", "Платежи", Icons.Rounded.Payment),
            Triple("saljut", "Ассистент", Icons.Rounded.Chat),
            Triple("profile", "Профиль", Icons.Rounded.Person)
        )
        
        menuItems.forEach { (tabId, label, iconVec) ->
            val isCurrent = selectedTab == tabId
            NavigationBarItem(
                selected = isCurrent,
                onClick = { onSelect(tabId) },
                icon = {
                    Icon(
                        imageVector = iconVec,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SberGreenBright,
                    selectedTextColor = SberGreenBright,
                    unselectedIconColor = Color.LightGray.copy(alpha = 0.6f),
                    unselectedTextColor = Color.LightGray.copy(alpha = 0.6f),
                    indicatorColor = Color(0xFF132F1A)
                )
            )
        }
    }
}

// ==========================================
// 3. TAB AREA CONTROLLER DELEGATOR
// ==========================================
@Composable
fun MainTabController(
    tab: String,
    userName: String,
    userPhone: String,
    cards: List<CardEntity>,
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    chatMessages: List<ChatMessageEntity>,
    viewModel: BankViewModel,
    onTxSelect: (TransactionEntity) -> Unit,
    onSignOutClient: () -> Unit
) {
    when (tab) {
        "home" -> HomeScreenTab(
            userName = userName,
            cards = cards,
            accounts = accounts,
            transactions = transactions,
            viewModel = viewModel,
            onSelectTransaction = onTxSelect,
            onLogOut = onSignOutClient
        )
        "payments" -> PaymentsScreenTab(
            cards = cards,
            viewModel = viewModel
        )
        "saljut" -> SalutAssistantTab(
            chatMessages = chatMessages,
            viewModel = viewModel
        )
        "profile" -> ProfileScreenTab(
            userName = userName,
            userPhone = userPhone,
            cards = cards,
            viewModel = viewModel,
            onLogOut = onSignOutClient
        )
    }
}

// ==========================================
// 4. HOME HUB TAB (HIGH FIDELITY SBER STYLE)
// ==========================================
@Composable
fun HomeScreenTab(
    userName: String,
    cards: List<CardEntity>,
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    viewModel: BankViewModel,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onLogOut: () -> Unit
) {
    val firstName = userName.split(" ").firstOrNull() ?: userName
    val sumTotalFunds = cards.sumOf { it.balance } + accounts.sumOf { it.balance }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SberBackgroundDark)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // App bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF1BE36E).copy(alpha = 0.15f), shape = CircleShape)
                            .border(1.dp, SberGreenBright, shape = CircleShape)
                            .clip(CircleShape)
                            .clickable { onLogOut() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstName.take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SberGreenBright
                        )
                    }
                    
                    Column {
                        Text("Кошелёк", fontSize = 12.sp, color = SberMutedText)
                        Text(userName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                
                IconButton(
                    onClick = onLogOut,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.05f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Выйти",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Replica of Search Input
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search icon",
                        tint = SberMutedText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Поиск услуг, контактов, аналитика", color = SberMutedText, fontSize = 14.sp)
                }
            }
        }

        // Cumulative Sber balance card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SberSurfaceDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ВСЕ СРЕДСТВА В СБЕРЕ",
                        fontSize = 11.sp,
                        color = SberGreenBright,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = viewModel.formatCurrency(sumTotalFunds),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Активных карт: ${cards.size} шт.  |  Вкладов: ${accounts.size} шт.",
                        fontSize = 13.sp,
                        color = SberMutedText
                    )
                }
            }
        }

        // Debit Cards Carousel
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Карты", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Icon(Icons.Rounded.CreditCard, contentDescription = "Debit Cards logo", tint = SberGreenBright, modifier = Modifier.size(20.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (cards.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(horizontal = 16.dp)
                            .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет карт в наличии. Откройте в админе.", color = SberMutedText, fontSize = 13.sp)
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(cards) { card ->
                            CreditCardMiniView(card = card, formatCurrency = { viewModel.formatCurrency(it) })
                        }
                    }
                }
            }
        }

        // Savings Accounts Accordion list (Вклады и Счета)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Вклады и счета",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (accounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("У вас нет накопительных программ.", color = SberMutedText, fontSize = 13.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        accounts.forEach { acc ->
                            AccountItemRow(account = acc, formatCurrency = { viewModel.formatCurrency(it) })
                        }
                    }
                }
            }
        }

        // Smart quick transactions log carousel
        item {
            Column {
                Text(
                    text = "Популярное",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val quickLinks = listOf(
                        Triple("Моё ЖКУ", "Квартплата", Icons.Rounded.AccountBalance),
                        Triple("Анна Т.", "+7 903 444-55-11", Icons.Rounded.Person),
                        Triple("МТС Связь", "Пополнение телефон", Icons.Rounded.Phone)
                    )
                    items(quickLinks) { (lbl, sub, iconVcc) ->
                        Box(
                            modifier = Modifier
                                .width(115.dp)
                                .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF1BE36E).copy(alpha = 0.1f), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(iconVcc, contentDescription = lbl, tint = SberGreenBright, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(lbl, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, color = SberMutedText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Transactions audit rows List
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("История операций", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Все", color = SberGreenBright, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет совершенных транзакций.", color = SberMutedText, fontSize = 13.sp)
                    }
                } else {
                    transactions.take(8).forEach { tx ->
                        TransactionItemRow(
                            tx = tx,
                            onSelect = onSelectTransaction,
                            formatCurrency = { viewModel.formatCurrency(it) }
                        )
                    }
                }
            }
        }
    }
}

// Compact visual mini-card layout (Sberbank Premium Skins)
@Composable
fun CreditCardMiniView(
    card: CardEntity,
    formatCurrency: (Double) -> String
) {
    val layoutGradient = when (card.styleName) {
        "Classic Green" -> Brush.verticalGradient(listOf(SkinClassicGreenStart, SkinClassicGreenEnd))
        "Emerald Gold" -> Brush.verticalGradient(listOf(SkinGoldStart, SkinGoldEnd))
        "Cyberpunk Black" -> Brush.verticalGradient(listOf(SkinMidnightStart, SkinMidnightEnd))
        "Cosmic Violet" -> Brush.verticalGradient(listOf(SkinNeonStart, SkinNeonEnd))
        "Sky Blue" -> Brush.verticalGradient(listOf(SkinBlueStart, SkinBlueEnd))
        else -> {
            try {
                val parsedColor = Color(android.graphics.Color.parseColor(card.colorHex))
                Brush.verticalGradient(listOf(parsedColor, parsedColor.copy(alpha = 0.5f)))
            } catch (e: Exception) {
                Brush.verticalGradient(listOf(SkinClassicGreenStart, SkinClassicGreenEnd))
            }
        }
    }

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(150.dp)
            .shadow(4.dp, shape = RoundedCornerShape(18.dp))
            .background(layoutGradient, shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.cardName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = card.cardType,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
            
            // Electronic chip simulation icon
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(20.dp)
                    .background(Color(0xFFE5C158).copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp))
            )
            
            Column {
                Text(
                    text = formatCurrency(card.balance),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = card.cardNumber,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "12/29",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// Deposit Item Row in Home Tab
@Composable
fun AccountItemRow(
    account: AccountEntity,
    formatCurrency: (Double) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFFF9500).copy(alpha = 0.1f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.TrendingUp,
                    contentDescription = "Interest Rate Icon",
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.accountName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Ставка ${account.interestRate}%  |  Счёт ${account.accountNumber}",
                    color = SberMutedText,
                    fontSize = 11.sp
                )
            }
        }
        
        Text(
            text = formatCurrency(account.balance),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
    }
}

// Individual Transaction Rows in Home Tab
@Composable
fun TransactionItemRow(
    tx: TransactionEntity,
    onSelect: (TransactionEntity) -> Unit,
    formatCurrency: (Double) -> String
) {
    val isDebit = tx.type == "TRANSFER_OUT" || tx.type == "PAYMENT"
    
    val categoryIconVector = when (tx.category) {
        "Супермаркеты" -> Icons.Rounded.Home
        "Кафе и рестораны" -> Icons.Rounded.Home
        "Связь" -> Icons.Rounded.Phone
        "Переводы" -> Icons.Rounded.Sync
        else -> Icons.Rounded.Home
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelect(tx) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isDebit) Color.White.copy(alpha = 0.05f) else Color(0xFF1BE36E).copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIconVector,
                    contentDescription = tx.category,
                    tint = if (isDebit) Color.LightGray else SberGreenBright,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDebit) tx.recipientName else "Пополнение",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDebit) "${tx.category} • ${tx.senderName}" else "от ${tx.senderName}",
                    color = SberMutedText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Text(
            text = "${if (isDebit) "-" else "+"}${formatCurrency(tx.amount)}",
            color = if (isDebit) Color.White else SberGreenBright,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp
        )
    }
}

// ==========================================
// 5. PAYMENTS & TRANSFER FORM MODULE
// ==========================================
@Composable
fun PaymentsScreenTab(
    cards: List<CardEntity>,
    viewModel: BankViewModel
) {
    val flowStatus by viewModel.transferStatus.collectAsStateWithLifecycle()
    
    // Core states
    var cardIndexForDeduction by remember { mutableStateOf(0) }
    var inputBeneficiaryName by remember { mutableStateOf("") }
    var inputBeneficiaryPhone by remember { mutableStateOf("") }
    var inputTransferAmount by remember { mutableStateOf("") }
    var inputGreetingMessage by remember { mutableStateOf("") }
    
    // Utility pay visual toggle
    var activeParticularPaymentCategory by remember { mutableStateOf<String?>(null) } // "mobile", "utilities" or null
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SberBackgroundDark)
            .statusBarsPadding()
    ) {
        when {
            flowStatus is TransferProgress.Processing -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = SberGreenBright, strokeWidth = 5.dp, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("ЭДО Транзакция обрабатывается...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Секунду, защищаем цифровой перевод...", color = SberMutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            
            flowStatus is TransferProgress.Success -> {
                val successfulReceipt = (flowStatus as TransferProgress.Success).transaction
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .background(SberGreenPrimary.copy(alpha = 0.15f), shape = CircleShape)
                                .border(2.dp, SberGreenBright, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Успешно",
                                tint = SberGreenBright,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text("Перевод выполнен!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = viewModel.formatCurrency(successfulReceipt.amount),
                            color = SberGreenBright,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(30.dp))
                        
                        // Transaction Details Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Кому в Сбер:", color = SberMutedText, fontSize = 13.sp)
                                Text(successfulReceipt.recipientName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Реквизиты/Телефон:", color = SberMutedText, fontSize = 13.sp)
                                Text(successfulReceipt.recipientPhone, color = Color.White, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Категория перевода:", color = SberMutedText, fontSize = 13.sp)
                                Text(successfulReceipt.category, color = Color.White, fontSize = 13.sp)
                            }
                            if (successfulReceipt.notes.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Сообщение:", color = SberMutedText, fontSize = 13.sp)
                                    Text(successfulReceipt.notes, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            viewModel.clearTransferStatus()
                            inputBeneficiaryName = ""
                            inputBeneficiaryPhone = ""
                            inputTransferAmount = ""
                            inputGreetingMessage = ""
                            activeParticularPaymentCategory = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Вернуться в Платежи", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            else -> {
                // Client Payment formulation view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Text(
                            text = "Платежи и переводы",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    
                    // Display error if present
                    if (flowStatus is TransferProgress.Error) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1619)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = (flowStatus as TransferProgress.Error).message,
                                    color = Color(0xFFFFB4BB),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    if (activeParticularPaymentCategory == null) {
                        item {
                            Text("Перевод клиенту Сбера по номеру телефона", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                        
                        // Render origin card selection
                        item {
                            if (cards.isEmpty()) {
                                Text("Нет доступных платежных карт. Откройте карту в ПУЛЬТЕ АДМИНИСТРАТОРА.", color = SberMutedText, fontSize = 13.sp)
                            } else {
                                val deductionCard = cards.getOrNull(cardIndexForDeduction)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        Text("Списать средства с карты:", color = SberMutedText, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(16.dp).background(SberGreenPrimary, shape = CircleShape))
                                                Text(
                                                    text = "${deductionCard?.cardName} (${deductionCard?.cardNumber})",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                text = viewModel.formatCurrency(deductionCard?.balance ?: 0.0),
                                                color = SberGreenBright,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        
                                        if (cards.size > 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Сменить карту списания >",
                                                color = SberGreenBright,
                                                fontSize = 11.sp,
                                                modifier = Modifier.clickable {
                                                    cardIndexForDeduction = (cardIndexForDeduction + 1) % cards.size
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Inputs
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = inputBeneficiaryName,
                                    onValueChange = { inputBeneficiaryName = it },
                                    label = { Text("ФИО Получателя") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                OutlinedTextField(
                                    value = inputBeneficiaryPhone,
                                    onValueChange = { inputBeneficiaryPhone = it },
                                    label = { Text("Номер телефона (+7...)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                OutlinedTextField(
                                    value = inputTransferAmount,
                                    onValueChange = { inputTransferAmount = it },
                                    label = { Text("Сумма перевода (₽)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                OutlinedTextField(
                                    value = inputGreetingMessage,
                                    onValueChange = { inputGreetingMessage = it },
                                    label = { Text("Сообщение к переводу (необязательно)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                            }
                        }
                        
                        // Sber slide to pay simulation trigger
                        item {
                            Button(
                                onClick = {
                                    val actCard = cards.getOrNull(cardIndexForDeduction)
                                    val parsedSum = inputTransferAmount.toDoubleOrNull() ?: 0.0
                                    if (actCard != null) {
                                        viewModel.performTransfer(
                                            sourceCardId = actCard.id,
                                            recipientName = inputBeneficiaryName,
                                            recipientPhone = inputBeneficiaryPhone,
                                            amount = parsedSum,
                                            transferNotes = inputGreetingMessage
                                        )
                                    }
                                },
                                enabled = cards.isNotEmpty() && inputBeneficiaryName.isNotBlank() && inputTransferAmount.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SberGreenPrimary,
                                    disabledContainerColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ПОДТВЕРДИТЬ СДЕЛКУ В СБЕРЕ", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        
                        // Secondary services payment circles
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Оплата коммунальных услуг и связи", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                                        .clickable {
                                            activeParticularPaymentCategory = "mobile"
                                            inputBeneficiaryName = "МТС Пополнение"
                                            inputBeneficiaryPhone = "+7 915 999-88-22"
                                            inputGreetingMessage = "Recharge"
                                            inputTransferAmount = ""
                                        }
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.Phone, contentDescription = "Мобильный", tint = SberGreenBright, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Мобильный", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                                        .clickable {
                                            activeParticularPaymentCategory = "utilities"
                                            inputBeneficiaryName = "ЖКУ Москва Квартплата"
                                            inputBeneficiaryPhone = "Лицевой счёт 1234567"
                                            inputGreetingMessage = "Коммуналка"
                                            inputTransferAmount = ""
                                        }
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.AccountBalance, contentDescription = "Квартплата ЖКХ", tint = SberGreenBright, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Квартплата ЖКУ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // Category sub payment
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeParticularPaymentCategory = null },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = SberGreenBright)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Назад к переводам", color = SberGreenBright, fontSize = 14.sp)
                            }
                        }
                        
                        item {
                            Text(
                                text = "Услуга: ${if (activeParticularPaymentCategory == "mobile") "Мобильный телефон" else "Квартплата ЖКУ"}",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                        
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = inputBeneficiaryPhone,
                                    onValueChange = { inputBeneficiaryPhone = it },
                                    label = { Text(if (activeParticularPaymentCategory == "mobile") "Номер телефона" else "Единый Лицевой Счёт") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                OutlinedTextField(
                                    value = inputTransferAmount,
                                    onValueChange = { inputTransferAmount = it },
                                    label = { Text("Сумма к оплате (₽)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                            }
                        }
                        
                        item {
                            Button(
                                onClick = {
                                    val currentCardSrc = cards.getOrNull(cardIndexForDeduction)
                                    val dynamicSum = inputTransferAmount.toDoubleOrNull() ?: 0.0
                                    if (currentCardSrc != null) {
                                        viewModel.performPayment(
                                            sourceCardId = currentCardSrc.id,
                                            providerName = inputBeneficiaryName,
                                            phoneNumber = inputBeneficiaryPhone,
                                            amount = dynamicSum,
                                            category = if (activeParticularPaymentCategory == "mobile") "Связь" else "Услуги ЖКХ"
                                        )
                                    }
                                },
                                enabled = cards.isNotEmpty() && inputBeneficiaryPhone.isNotBlank() && inputTransferAmount.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Оплатить услуги Сбера", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. SALJUT CHATBOT COMPOSABLE (AI ASSISTANT WITH GEMINI INTEGRATION)
// ==========================================
@Composable
fun SalutAssistantTab(
    chatMessages: List<ChatMessageEntity>,
    viewModel: BankViewModel
) {
    val isAssistantLoading by viewModel.isAssistantLoading.collectAsStateWithLifecycle()
    var userTextPrompt by remember { mutableStateOf("") }
    
    val chatScroll = rememberScrollState()
    
    // Auto scroll down with history
    LaunchedEffect(chatMessages.size) {
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SberBackgroundDark)
            .statusBarsPadding()
    ) {
        // AI Salut Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SberSurfaceDark)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF00FFCC), SberGreenPrimary)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Assistant,
                    contentDescription = "Salut ИИ лого",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Ассистент Салют", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Text(
                    text = if (isAssistantLoading) "Салют думает над решением..." else "Ваш интеллектуальный ИИ-банкир онлайн",
                    color = if (isAssistantLoading) SberGreenBright else SberMutedText,
                    fontSize = 11.sp
                )
            }
        }
        
        // Chat list container
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(chatScroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            chatMessages.forEach { msg ->
                val isUsr = msg.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUsr) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(
                                color = if (isUsr) Color(0xFF132F1A) else SberSurfaceDark,
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUsr) 16.dp else 0.dp,
                                    bottomEnd = if (isUsr) 0.dp else 16.dp
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = if (isUsr) SberGreenPrimary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUsr) 16.dp else 0.dp,
                                    bottomEnd = if (isUsr) 0.dp else 16.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.messageText,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            if (isAssistantLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = SberGreenBright, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Салют анализирует расходы...", color = SberMutedText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        
        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SberSurfaceDark)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userTextPrompt,
                onValueChange = { userTextPrompt = it },
                placeholder = { Text("Задайте вопрос Салюту...", color = SberMutedText, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                maxLines = 1,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SberBackgroundDark,
                    unfocusedContainerColor = SberBackgroundDark,
                    focusedIndicatorColor = SberGreenPrimary,
                    unfocusedIndicatorColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(24.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    if (userTextPrompt.isNotBlank()) {
                        viewModel.sendMessageToAssistant(userTextPrompt)
                        userTextPrompt = ""
                    }
                },
                enabled = userTextPrompt.isNotBlank() && !isAssistantLoading,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (userTextPrompt.isNotBlank()) SberGreenPrimary else Color.DarkGray,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = "Отправить",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// 7. PROFILE & DESIGN SKIN OPTION TAB
// ==========================================
@Composable
fun ProfileScreenTab(
    userName: String,
    userPhone: String,
    cards: List<CardEntity>,
    viewModel: BankViewModel,
    onLogOut: () -> Unit
) {
    var skinSelectionCardIndex by remember { mutableStateOf(0) }
    
    val premiumSkins = listOf(
        Pair("Classic Green", "#00A34B"),
        Pair("Emerald Gold", "#B89742"),
        Pair("Cyberpunk Black", "#0D1B2A"),
        Pair("Cosmic Violet", "#FF007F"),
        Pair("Sky Blue", "#00B4DB")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SberBackgroundDark)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Настройки Профиля",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // Profile block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SberSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(SberGreenPrimary.copy(alpha = 0.2f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, contentDescription = "Аватар", tint = SberGreenBright, modifier = Modifier.size(32.dp))
                    }
                    Column {
                        Text(userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(userPhone, color = SberMutedText, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Уровень лояльности: СберПрайм+", color = SberGreenBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card style design skin customization playground
        item {
            Text("Кастомизация дизайна СберКарты", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        if (cards.isNotEmpty()) {
            item {
                val chosenCard = cards.getOrNull(skinSelectionCardIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SberSurfaceDark, shape = RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Кастомизировать дизайн карты:", color = SberMutedText, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${chosenCard?.cardName} (${chosenCard?.cardNumber})",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (cards.size > 1) {
                                Text(
                                    text = "Выбрать другую >",
                                    color = SberGreenBright,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        skinSelectionCardIndex = (skinSelectionCardIndex + 1) % cards.size
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Render skin options
            item {
                Text(
                    text = "Выберите премиальный скин:",
                    color = SberMutedText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val targetingCard = cards.getOrNull(skinSelectionCardIndex)
                    premiumSkins.forEach { (skName, skHex) ->
                        val isCurrentSelectedSkin = targetingCard?.styleName == skName
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isCurrentSelectedSkin) Color(0xFF132F1A) else SberSurfaceDark,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrentSelectedSkin) SberGreenBright else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (targetingCard != null) {
                                        viewModel.updateCardSkin(targetingCard.id, skHex, skName)
                                    }
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(android.graphics.Color.parseColor(skHex)),
                                                    Color(android.graphics.Color.parseColor(skHex)).copy(alpha = 0.4f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                )
                                Text(skName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            
                            if (isCurrentSelectedSkin) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = SberGreenBright)
                            } else {
                                Text("Применить", color = SberMutedText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text("Дебетовые карты отсутствуют. Выпустите карту в консоли администрирования.", color = SberMutedText)
            }
        }

        // Reset database button
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.resetAllData() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("СБРОСИТЬ ДАННЫЕ МИХАИЛА К ДЕФОЛТУ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Button(
                onClick = onLogOut,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A141A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ВЫЙТИ ИЗ СБЕРБАНКА", color = Color(0xFFFFB4BB), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// 8. ADMINISTRATIVE WORKSPACE (PULS)
// ==========================================
@Composable
fun AdminDashboardScreen(
    userName: String,
    userPhone: String,
    cards: List<CardEntity>,
    accounts: List<AccountEntity>,
    viewModel: BankViewModel,
    onCloseAdmin: () -> Unit
) {
    var adminActiveSection by remember { mutableStateOf("profile") } // "profile", "cards", "accounts", "simulation"
    
    // States for custom profile updates
    var editClientNameValue by remember { mutableStateOf(userName) }
    var editClientPhoneValue by remember { mutableStateOf(userPhone) }
    
    // Balance editing
    var selectedBalanceCardIndex by remember { mutableStateOf(0) }
    var inputNewCardBalance by remember { mutableStateOf("") }
    
    // Card creation
    var adminCreatedCardName by remember { mutableStateOf("") }
    var adminCreatedCardBrand by remember { mutableStateOf("MIR") }
    var adminCreatedCardStyleName by remember { mutableStateOf("Classic Green") }
    
    // Account editing
    var selectedBalanceAccountIndex by remember { mutableStateOf(0) }
    var inputNewAccountBalance by remember { mutableStateOf("") }
    var inputNewAccountRate by remember { mutableStateOf("") }
    
    // New Account creation
    var adminCreatedAccountName by remember { mutableStateOf("") }
    var adminCreatedAccountBalance by remember { mutableStateOf("10000") }
    var adminCreatedAccountRate by remember { mutableStateOf("16") }
    
    // Simulating incoming transfers
    var simSenderNameInput by remember { mutableStateOf("ГОСУСЛУГИ") }
    var simAmountInput by remember { mutableStateOf("50000") }
    var simNotesInput by remember { mutableStateOf("Выплата пособия / Профицит бюджета") }
    var simTargetCardIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070C08)) // extremely dark administrative control slate
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Administrative Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111E13))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = "Admin System Icon", tint = SberGreenBright, modifier = Modifier.size(26.dp))
                Column {
                    Text("ПУЛЬТ СОТРУДНИКА СБЕРА", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, letterSpacing = 1.sp)
                    Text("Режим ручной калибровки баланса банка", color = SberMutedText, fontSize = 11.sp)
                }
            }
            
            Button(
                onClick = onCloseAdmin,
                colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text("Выход", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Administrative Section tabs scrollable Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C140E))
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabs = listOf(
                Pair("profile", "Клиент"),
                Pair("cards", "Карты"),
                Pair("accounts", "Вклады"),
                Pair("simulation", "Накатить перевод")
            )
            tabs.forEach { (tid, label) ->
                val active = adminActiveSection == tid
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .background(
                            color = if (active) SberGreenPrimary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { adminActiveSection = tid }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.White else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Dynamic administrative content scrolling region
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (adminActiveSection) {
                
                // --- ADMIN EDIT CLIENT PROFILE ---
                "profile" -> {
                    Text("Настройки ФИО и реквизитов клиента", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = editClientNameValue,
                        onValueChange = { editClientNameValue = it },
                        label = { Text("ФИО Клиента") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    OutlinedTextField(
                        value = editClientPhoneValue,
                        onValueChange = { editClientPhoneValue = it },
                        label = { Text("Телефон клиента") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    
                    Button(
                        onClick = {
                            viewModel.updateProfile(editClientNameValue, editClientPhoneValue)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ОБНОВИТЬ ПРОФИЛЬ КЛИЕНТА", color = Color.White)
                    }
                    
                    Text(
                        text = "Отредактированный ФИО подставится в шапку Сбера, карточки и историю начислений автоматически.",
                        color = SberMutedText,
                        fontSize = 12.sp
                    )
                }
                
                // --- ADMIN MANAGE CARDS ---
                "cards" -> {
                    Text("Управление балансом дебетовых карт", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    
                    if (cards.isEmpty()) {
                        Text("Нет активных запущенных карт в системе.", color = SberMutedText, fontSize = 13.sp)
                    } else {
                        val activeCardForEdit = cards.getOrNull(selectedBalanceCardIndex)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SberSurfaceDark, shape = RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Балансируемая карта:", fontSize = 11.sp, color = SberMutedText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${activeCardForEdit?.cardName} (${activeCardForEdit?.cardNumber}) — Баланс: ${viewModel.formatCurrency(activeCardForEdit?.balance ?: 0.0)}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (cards.size > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Выбрать другую для перезаписи >",
                                        color = SberGreenBright,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            selectedBalanceCardIndex = (selectedBalanceCardIndex + 1) % cards.size
                                            inputNewCardBalance = ""
                                        }
                                    )
                                }
                            }
                        }
                        
                        if (activeCardForEdit != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputNewCardBalance,
                                    onValueChange = { inputNewCardBalance = it },
                                    label = { Text("Новый баланс (₽)") },
                                    placeholder = { Text(activeCardForEdit.balance.toString()) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                Button(
                                    onClick = {
                                        val parseVal = inputNewCardBalance.toDoubleOrNull()
                                        if (parseVal != null) {
                                            viewModel.adminUpdateCardBalance(activeCardForEdit.id, parseVal)
                                            inputNewCardBalance = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary)
                                ) {
                                    Text("Принять")
                                }
                            }
                            
                            Button(
                                onClick = {
                                    viewModel.adminDeleteCard(activeCardForEdit.id)
                                    selectedBalanceCardIndex = 0
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A141A)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("УДАЛИТЬ КАРТУ ${activeCardForEdit.cardNumber}", color = Color.White)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color.DarkGray)
                    
                    Text("Выпустить новую пластиковую карту клиента", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = adminCreatedCardName,
                        onValueChange = { adminCreatedCardName = it },
                        label = { Text("Имя карты (например СберКарта Прайм)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    
                    // Style name picker
                    Column {
                        Text("Премиальный стиль (Скин):", color = SberMutedText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val designs = listOf("Classic Green", "Emerald Gold", "Cyberpunk Black", "Cosmic Violet", "Sky Blue")
                            designs.forEach { dsName ->
                                val active = adminCreatedCardStyleName == dsName
                                Box(
                                    modifier = Modifier
                                        .background(if (active) SberGreenPrimary else SberSurfaceDark, shape = RoundedCornerShape(10.dp))
                                        .clickable { adminCreatedCardStyleName = dsName }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(dsName, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    // Brand selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("MIR", "VISA", "MASTERCARD").forEach { brand ->
                            val active = adminCreatedCardBrand == brand
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) SberGreenPrimary else SberSurfaceDark, shape = RoundedCornerShape(10.dp))
                                    .clickable { adminCreatedCardBrand = brand }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(brand, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            if (adminCreatedCardName.isNotBlank()) {
                                val sHex = when (adminCreatedCardStyleName) {
                                    "Classic Green" -> "#00A34B"
                                    "Emerald Gold" -> "#B89742"
                                    "Cyberpunk Black" -> "#0D1B2A"
                                    "Cosmic Violet" -> "#FF007F"
                                    "Sky Blue" -> "#00B4DB"
                                    else -> "#00A34B"
                                }
                                viewModel.createNewCard(adminCreatedCardName, adminCreatedCardStyleName, sHex, adminCreatedCardBrand)
                                adminCreatedCardName = ""
                            }
                        },
                        enabled = adminCreatedCardName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ОТКРЫТЬ КАРТУ КЛИЕНТУ", color = Color.White)
                    }
                }
                
                // --- ADMIN MANAGE ACCOUNTS ---
                "accounts" -> {
                    Text("Регулирование вкладов и ставок счета", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    
                    if (accounts.isEmpty()) {
                        Text("У клиента нет активных накоплений в Сбере.", color = SberMutedText, fontSize = 13.sp)
                    } else {
                        val targetedAccForEdit = accounts.getOrNull(selectedBalanceAccountIndex)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SberSurfaceDark, shape = RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Настраиваемый вклад:", fontSize = 11.sp, color = SberMutedText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${targetedAccForEdit?.accountName} (${targetedAccForEdit?.accountNumber}) — Баланс: ${viewModel.formatCurrency(targetedAccForEdit?.balance ?: 0.0)} [Ставка: ${targetedAccForEdit?.interestRate}%]",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (accounts.size > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Выбрать другой накопительный счёт >",
                                        color = SberGreenBright,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            selectedBalanceAccountIndex = (selectedBalanceAccountIndex + 1) % accounts.size
                                            inputNewAccountBalance = ""
                                            inputNewAccountRate = ""
                                        }
                                    )
                                }
                            }
                        }
                        
                        if (targetedAccForEdit != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputNewAccountBalance,
                                    onValueChange = { inputNewAccountBalance = it },
                                    label = { Text("Новый баланс") },
                                    placeholder = { Text(targetedAccForEdit.balance.toString()) },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                                OutlinedTextField(
                                    value = inputNewAccountRate,
                                    onValueChange = { inputNewAccountRate = it },
                                    label = { Text("Ставка %") },
                                    placeholder = { Text(targetedAccForEdit.interestRate.toString()) },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                                )
                            }
                            
                            Button(
                                onClick = {
                                    val parsedB = inputNewAccountBalance.toDoubleOrNull() ?: targetedAccForEdit.balance
                                    val parsedR = inputNewAccountRate.toDoubleOrNull() ?: targetedAccForEdit.interestRate
                                    viewModel.adminUpdateAccountBalance(targetedAccForEdit.id, parsedB, parsedR)
                                    inputNewAccountBalance = ""
                                    inputNewAccountRate = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ЗАПИСАТЬ ДАННЫЕ СЧЕТА")
                            }
                            
                            Button(
                                onClick = {
                                    viewModel.adminDeleteAccount(targetedAccForEdit.id)
                                    selectedBalanceAccountIndex = 0
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A141A)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ЗАКРЫТЬ ВКЛАД ${targetedAccForEdit.accountName}", color = Color.White)
                            }
                        }
                    }
                    
                    HorizontalDivider(color = Color.DarkGray)
                    
                    Text("Создать новый сберегательный вклад", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    
                    OutlinedTextField(
                        value = adminCreatedAccountName,
                        onValueChange = { adminCreatedAccountName = it },
                        label = { Text("Уникальное Название Вклада") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = adminCreatedAccountBalance,
                            onValueChange = { adminCreatedAccountBalance = it },
                            label = { Text("Депозит") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                        )
                        OutlinedTextField(
                            value = adminCreatedAccountRate,
                            onValueChange = { adminCreatedAccountRate = it },
                            label = { Text("Процент %") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                        )
                    }
                    
                    Button(
                        onClick = {
                            if (adminCreatedAccountName.isNotBlank()) {
                                val sBal = adminCreatedAccountBalance.toDoubleOrNull() ?: 10000.0
                                val sRate = adminCreatedAccountRate.toDoubleOrNull() ?: 16.0
                                viewModel.adminAddAccount(adminCreatedAccountName, sBal, sRate)
                                adminCreatedAccountName = ""
                            }
                        },
                        enabled = adminCreatedAccountName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ОТКРЫТЬ ВКЛАД В БАЗЕ", color = Color.White)
                    }
                }
                
                // --- ADMIN SIMULATE INCOMING REMITTANCE ---
                "simulation" -> {
                    Text("Симуляция поступления средств (Переводы/Зарплата)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    Text(
                        text = "После зачисления средств сработает всплывающий системный SMS-баннер и обновится баланс указанной карты клиента.",
                        fontSize = 11.sp,
                        color = SberMutedText
                    )
                    
                    OutlinedTextField(
                        value = simSenderNameInput,
                        onValueChange = { simSenderNameInput = it },
                        label = { Text("Отправитель (например МАМА, ЗАРАБОТНАЯ ПЛАТА)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    OutlinedTextField(
                        value = simAmountInput,
                        onValueChange = { simAmountInput = it },
                        label = { Text("Сумма зачисления (₽)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    OutlinedTextField(
                        value = simNotesInput,
                        onValueChange = { simNotesInput = it },
                        label = { Text("Сообщение зачисления") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = SberSurfaceDark, unfocusedContainerColor = SberSurfaceDark, focusedIndicatorColor = SberGreenPrimary)
                    )
                    
                    if (cards.isNotEmpty()) {
                        val tarCard = cards.getOrNull(simTargetCardIndex)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SberSurfaceDark, shape = RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("На какую карту начислить деньги:", fontSize = 11.sp, color = SberMutedText)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${tarCard?.cardName} (${tarCard?.cardNumber})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (cards.size > 1) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Выбрать другую карту в реквизитах >",
                                        color = SberGreenBright,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            simTargetCardIndex = (simTargetCardIndex + 1) % cards.size
                                        }
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = {
                                val sAmt = simAmountInput.toDoubleOrNull() ?: 50000.0
                                if (tarCard != null) {
                                    viewModel.adminAddIncomingTransaction(
                                        senderName = simSenderNameInput,
                                        amount = sAmt,
                                        category = "Переводы",
                                        recipientCardId = tarCard.id,
                                        notes = simNotesInput
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SberGreenBright),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("НАЧИСЛИТЬ СРЕДСТВА И ВЫСЛАТЬ SMS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Внимание: Сначала откройте карту в разделе 'Карты'!", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
        
        // Bottom Return Button
        Button(
            onClick = onCloseAdmin,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("ВЕРНУТЬСЯ НА ЭКРАН ВХОДА БАНКА", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 9. DIALOG DETAILS TRANSACTION POPUP
// ==========================================
@Composable
fun TransactionDetailsDialog(
    tx: TransactionEntity,
    onDismiss: () -> Unit,
    formatCurrency: (Double) -> String
) {
    val isDebit = tx.type == "TRANSFER_OUT" || tx.type == "PAYMENT"
    val representationDate = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm", java.util.Locale("ru")).format(java.util.Date(tx.timestamp))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SberSurfaceDark),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Receipt head visual
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isDebit) "Расходный платёж" else "Зачисление средств",
                        color = SberMutedText,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${if (isDebit) "-" else "+"}${formatCurrency(tx.amount)}",
                        color = if (isDebit) Color.White else SberGreenBright,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                HorizontalDivider(color = Color.DarkGray)
                
                // Detailed key value specs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Кому / Отправитель:", color = SberMutedText, fontSize = 13.sp)
                    Text(if (isDebit) tx.recipientName else tx.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Категория платежа:", color = SberMutedText, fontSize = 13.sp)
                    Text(tx.category, color = Color.White, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Время проведения:", color = SberMutedText, fontSize = 13.sp)
                    Text(representationDate, color = Color.White, fontSize = 13.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Статус перевода:", color = SberMutedText, fontSize = 13.sp)
                    Text(tx.status, color = SberGreenBright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (tx.notes.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Сообщение:", color = SberMutedText, fontSize = 13.sp)
                        Text(tx.notes, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SberGreenPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ВЕРНУТЬСЯ К ОПЕРАЦИЯМ", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==========================================
// 10. SYSTEM MOBILE SMS FLOATING NOTIFICATION BANNER
// ==========================================
@Composable
fun ToastIncomingNotification(
    ntf: MockNotification,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E21)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape = RoundedCornerShape(16.dp))
            .border(1.dp, SberGreenBright, shape = RoundedCornerShape(16.dp))
            .clickable { onDismiss() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SberGreenPrimary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Пуш оповещение Сбера",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ntf.title,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = ntf.message,
                    color = SberOnSurfaceDark,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
