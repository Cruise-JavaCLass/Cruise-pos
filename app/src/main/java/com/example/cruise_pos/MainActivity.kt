package com.example.cruise_pos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cruise.pos.shared.core.network.ApiResult
import com.cruise.pos.shared.data.repository.ShiftRepository
import com.cruise.pos.shared.data.repository.ShiftRepositoryImpl
import com.cruise.pos.shared.domain.model.*
import com.cruise.pos.shared.domain.service.GuestCardRecognitionService
import com.cruise.pos.shared.domain.service.InMemoryGuestDirectoryRepository
import com.example.cruise_pos.auth.SupervisorAuthModal
import com.example.cruise_pos.ui.catalog.CatalogMenuScreen
import com.example.cruise_pos.ui.scanner.CameraXScanningViewfinder
import com.example.cruise_pos.ui.scanner.GuestIdentityCardPopup
import com.example.cruise_pos.ui.scanner.NfcCardReaderOverlay
import com.example.cruise_pos.ui.shift.ActiveShiftStateBanner
import com.example.cruise_pos.ui.shift.CashDropDeclarationDialog
import com.example.cruise_pos.ui.theme.CruiseposTheme
import kotlinx.coroutines.launch

private val CruiseNavy = Color(0xFF0A2342)
private val OceanTeal = Color(0xFF0284C7)
private val LightBg = Color(0xFFF8FAFC)

enum class PosTab(val title: String, val icon: String) {
    MENU("Order Menu", "🍹"),
    GUEST("Guest Scan", "🪪"),
    SHIFT("Shift & Safe", "💼")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CruiseposTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// ── Sample Menu Catalog Data for Cruise POS ─────────────────────────
private val sampleCategories = listOf(
    MenuCategory(id = "ALL", name = "All Items", displayOrder = 0),
    MenuCategory(id = "COCKTAILS", name = "Cocktails & Drinks", displayOrder = 1),
    MenuCategory(id = "COFFEE", name = "Cafe & Bakery", displayOrder = 2),
    MenuCategory(id = "FOOD", name = "Deck Dining", displayOrder = 3),
    MenuCategory(id = "DUTY_FREE", name = "Duty-Free Goods", displayOrder = 4)
)

private val sampleMenuItems = listOf(
    MenuItem(
        id = "COCKTAIL_MOJITO",
        categoryId = "COCKTAILS",
        name = "Classic Mint Mojito",
        description = "White rum, fresh lime, crushed mint leaves, sparkling soda",
        basePrice = 12.00,
        modifierGroups = listOf(
            ModifierGroup(
                id = "ICE",
                name = "Ice Preference",
                minSelections = 1,
                maxSelections = 1,
                options = listOf(
                    ModifierOption("ICE_NORMAL", "Regular Ice", 0.0, isDefault = true),
                    ModifierOption("ICE_LESS", "Less Ice", 0.0),
                    ModifierOption("ICE_NONE", "No Ice", 0.0)
                )
            ),
            ModifierGroup(
                id = "SUGAR",
                name = "Sweetness",
                minSelections = 1,
                maxSelections = 1,
                options = listOf(
                    ModifierOption("SUGAR_100", "100% Sugar", 0.0, isDefault = true),
                    ModifierOption("SUGAR_50", "50% Sugar", 0.0),
                    ModifierOption("SUGAR_0", "Unsweetened", 0.0)
                )
            )
        )
    ),
    MenuItem(
        id = "COCKTAIL_PINA",
        categoryId = "COCKTAILS",
        name = "Caribbean Piña Colada",
        description = "Coconut cream, fresh pineapple juice, aged Caribbean rum",
        basePrice = 14.00
    ),
    MenuItem(
        id = "VIRGIN_COLADA",
        categoryId = "COCKTAILS",
        name = "Virgin Colada (Zero-Proof)",
        description = "Non-alcoholic blend of chilled coconut & pineapple",
        basePrice = 8.50
    ),
    MenuItem(
        id = "COFFEE_AMERICANO",
        categoryId = "COFFEE",
        name = "Iced Americano",
        description = "Double shot espresso poured over chilled mountain water",
        basePrice = 4.50,
        modifierGroups = listOf(
            ModifierGroup(
                id = "SIZE",
                name = "Cup Size",
                minSelections = 1,
                maxSelections = 1,
                options = listOf(
                    ModifierOption("REG", "Regular 12oz", 0.0, isDefault = true),
                    ModifierOption("LRG", "Large 16oz (+ $1.00)", 1.00)
                )
            )
        )
    ),
    MenuItem(
        id = "COFFEE_LATTE",
        categoryId = "COFFEE",
        name = "Sea Salt Vanilla Latte",
        description = "Espresso, organic oat milk, Madagascar vanilla, sea salt foam",
        basePrice = 5.50
    ),
    MenuItem(
        id = "FOOD_BURGER",
        categoryId = "FOOD",
        name = "Cruise Wagyu Burger",
        description = "Brioche bun, aged cheddar, caramelized onions, truffle sauce",
        basePrice = 18.50,
        modifierGroups = listOf(
            ModifierGroup(
                id = "SIDE",
                name = "Included Side",
                minSelections = 1,
                maxSelections = 1,
                options = listOf(
                    ModifierOption("FRIES", "Crispy French Fries", 0.0, isDefault = true),
                    ModifierOption("SALAD", "Garden Salad", 0.0),
                    ModifierOption("TRUFFLE_FRIES", "Truffle Fries (+ $2.50)", 2.50)
                )
            )
        )
    ),
    MenuItem(
        id = "FOOD_FRIES",
        categoryId = "FOOD",
        name = "Parmesan Truffle Fries",
        description = "Hand-cut potatoes, grated parmesan, white truffle oil",
        basePrice = 9.00
    ),
    MenuItem(
        id = "FOOD_CHEESECAKE",
        categoryId = "FOOD",
        name = "New York Cheesecake",
        description = "Classic baked cheesecake with raspberry coulis",
        basePrice = 7.00,
        isAvailable = false // 86ed! Sold out demonstration!
    ),
    MenuItem(
        id = "DUTY_FREE_RUM",
        categoryId = "DUTY_FREE",
        name = "Havana Club 7-Year (750ml)",
        description = "Duty-free bottle, delivered directly to passenger cabin",
        basePrice = 45.00
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    shiftRepository: ShiftRepository = remember { ShiftRepositoryImpl() },
    guestRecognitionService: GuestCardRecognitionService = remember { GuestCardRecognitionService() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(PosTab.MENU) }
    val cartItems = remember { mutableStateListOf<ConfiguredCartItem>() }

    // Guest card state
    var showQrScanner by remember { mutableStateOf(false) }
    var showNfcReader by remember { mutableStateOf(false) }
    var recognizedGuest by remember { mutableStateOf<GuestIdentity?>(null) }

    // Shift state
    var showSupervisorModal by remember { mutableStateOf(false) }
    var authResult by remember { mutableStateOf<String?>(null) }
    var showCashDropDialog by remember { mutableStateOf(false) }
    var dropResult by remember { mutableStateOf<String?>(null) }
    var currentDrawerCash by remember { mutableDoubleStateOf(1450.00) }
    var isSubmittingDrop by remember { mutableStateOf(false) }
    var dropErrorMessage by remember { mutableStateOf<String?>(null) }

    // Cart Dialog
    var showCartDialog by remember { mutableStateOf(false) }

    val activeShift = remember {
        ShiftRecord(
            id = "shift-pool-101",
            cashierId = 12L,
            cashierName = "Roving Crew (Deck 9)",
            stationId = "STATION_POOL_BAR",
            status = ShiftStatus.OPEN,
            startingFloat = 300.0,
            openedAt = "2026-10-25T08:00:00Z"
        )
    }

    LaunchedEffect(activeShift) {
        shiftRepository.setCurrentShift(activeShift)
    }

    val cartTotal = cartItems.sumOf { it.totalPrice }
    val totalItemCount = cartItems.sumOf { it.quantity }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CruiseNavy,
                    titleContentColor = Color.White
                ),
                title = {
                    Column {
                        Text(
                            text = "🚢 Cruise POS Terminal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Deck 9 Pool Bar • Cashier: ${activeShift.cashierName}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                },
                actions = {
                    // Quick QR Scanner icon
                    IconButton(onClick = { showQrScanner = true }) {
                        Text("📷", fontSize = 20.sp)
                    }
                    // Quick NFC Tap icon
                    IconButton(onClick = { showNfcReader = true }) {
                        Text("📶", fontSize = 20.sp)
                    }
                    // Cart Icon with badge
                    IconButton(onClick = { showCartDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (totalItemCount > 0) {
                                    Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                        Text("$totalItemCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "View Cart",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                PosTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.icon, fontSize = 20.sp) },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CruiseNavy,
                            selectedTextColor = CruiseNavy,
                            indicatorColor = OceanTeal.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(LightBg)
        ) {
            when (selectedTab) {
                // ── TAB 1: ORDER MENU & CATALOG ──────────────────────────────
                PosTab.MENU -> {
                    CatalogMenuScreen(
                        categories = sampleCategories,
                        menuItems = sampleMenuItems,
                        onAddToCart = { configuredItem ->
                            cartItems.add(configuredItem)
                            Toast.makeText(
                                context,
                                "Added ${configuredItem.quantity}x ${configuredItem.menuItem.name} to Cart!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }

                // ── TAB 2: GUEST CARD SCANNER & FOLIO ────────────────────────
                PosTab.GUEST -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Guest Card Recognition",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = CruiseNavy)
                        )
                        Text(
                            text = "Identify passenger by QR Camera barcode or contactless NFC tap",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Trigger buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showQrScanner = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = OceanTeal),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📷 Scan QR Pass")
                            }

                            Button(
                                onClick = { showNfcReader = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📶 Tap NFC Card")
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Quick demo passenger buttons
                        Text(
                            text = "Or tap a simulated guest for quick testing:",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF475569))
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val demoManifest = InMemoryGuestDirectoryRepository.defaultShipGuestManifest()
                        demoManifest.forEach { demoGuest ->
                            Card(
                                onClick = { recognizedGuest = demoGuest },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${demoGuest.fullName} (${demoGuest.passengerType.name})",
                                            fontWeight = FontWeight.Bold,
                                            color = CruiseNavy
                                        )
                                        Text(
                                            text = "Cabin ${demoGuest.cabinNumber} • Age: ${demoGuest.age} • ${demoGuest.packageTier}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Text(
                                        text = "$${"%.2f".format(demoGuest.availableSpendingLimit)} left",
                                        fontWeight = FontWeight.Bold,
                                        color = if (demoGuest.availableSpendingLimit > 50) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ── TAB 3: SHIFT & SAFE DROP ─────────────────────────────────
                PosTab.SHIFT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ActiveShiftStateBanner(
                            currentShift = activeShift,
                            onOpenShiftClick = {
                                Toast.makeText(context, "Shift is already active", Toast.LENGTH_SHORT).show()
                            },
                            onCloseShiftClick = {
                                Toast.makeText(context, "Closing shift requires Z-Report reconciliation", Toast.LENGTH_SHORT).show()
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Cash Drawer Balance",
                                    style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF64748B))
                                )
                                Text(
                                    text = "$${"%.2f".format(currentDrawerCash)}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (currentDrawerCash > 1000.0) Color(0xFFDC2626) else CruiseNavy
                                    )
                                )
                                if (currentDrawerCash > 1000.0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "⚠️ Limit exceeded ($1,000.00). Perform safe cash drop now.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { showCashDropDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Perform Cash Drop")
                            }

                            Button(
                                onClick = { showSupervisorModal = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Supervisor Auth")
                            }
                        }

                        if (dropResult != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                            ) {
                                Text(
                                    text = "✅ $dropResult",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        if (authResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E7FF))
                            ) {
                                Text(
                                    text = "🛡️ Authorized: $authResult",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4338CA), fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Cart Dialog ──────────────────────────────────────────────────────────
    if (showCartDialog) {
        AlertDialog(
            onDismissRequest = { showCartDialog = false },
            title = {
                Text(
                    text = "🛒 Current Order ($totalItemCount items)",
                    fontWeight = FontWeight.Bold,
                    color = CruiseNavy
                )
            },
            text = {
                if (cartItems.isEmpty()) {
                    Text("Your cart is empty. Tap items in Order Menu to add.")
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        cartItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.quantity}x ${item.menuItem.name}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.selectedModifiers.isNotEmpty()) {
                                        Text(
                                            text = item.selectedModifiers.joinToString(", ") { it.optionName },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Text(
                                    text = "$${"%.2f".format(item.totalPrice)}",
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { cartItems.removeAt(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red)
                                }
                            }
                            HorizontalDivider()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${"%.2f".format(cartTotal)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CruiseNavy
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (cartItems.isNotEmpty()) {
                    Button(
                        onClick = {
                            showCartDialog = false
                            selectedTab = PosTab.GUEST
                            Toast.makeText(context, "Select or scan passenger card to charge folio", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CruiseNavy)
                    ) {
                        Text("Charge to Guest Folio")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCartDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ── CameraX QR Scanning Modal ────────────────────────────────────────────
    if (showQrScanner) {
        CameraXScanningViewfinder(
            isScanningActive = true,
            onQrDetected = { qrCode ->
                showQrScanner = false
                when (val result = guestRecognitionService.parseQrPayload(qrCode)) {
                    is ScanRecognitionResult.Success -> {
                        recognizedGuest = result.guest
                    }
                    is ScanRecognitionResult.CardBlocked -> {
                        Toast.makeText(context, "CARD BLOCKED: ${result.reason}", Toast.LENGTH_LONG).show()
                    }
                    is ScanRecognitionResult.Expired -> {
                        Toast.makeText(context, "EXPIRED: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                    is ScanRecognitionResult.InvalidPayload -> {
                        Toast.makeText(context, "INVALID: ${result.error}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    // ── NFC Card Reader Modal ────────────────────────────────────────────────
    if (showNfcReader) {
        NfcCardReaderOverlay(
            isNfcListening = true,
            onCardTapped = { uid: String ->
                showNfcReader = false
                when (val result = guestRecognitionService.processNfcCardTap(uid)) {
                    is ScanRecognitionResult.Success -> {
                        recognizedGuest = result.guest
                    }
                    is ScanRecognitionResult.CardBlocked -> {
                        Toast.makeText(context, "CARD BLOCKED: ${result.reason}", Toast.LENGTH_LONG).show()
                    }
                    is ScanRecognitionResult.Expired -> {
                        Toast.makeText(context, "EXPIRED: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                    is ScanRecognitionResult.InvalidPayload -> {
                        Toast.makeText(context, "INVALID: ${result.error}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showNfcReader = false }
        )
    }

    // ── Guest Identity Card Popup ────────────────────────────────────────────
    recognizedGuest?.let { guest ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            GuestIdentityCardPopup(
                guest = guest,
                onChargeOrder = {
                    if (cartItems.isEmpty()) {
                        Toast.makeText(context, "Charged guest ${guest.fullName} folio (Cabin: ${guest.cabinNumber})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Charged $${"%.2f".format(cartTotal)} to Cabin ${guest.cabinNumber} Folio!", Toast.LENGTH_LONG).show()
                        cartItems.clear()
                    }
                    recognizedGuest = null
                },
                onDismiss = { recognizedGuest = null }
            )
        }
    }

    // ── Cash Drop Declaration Dialog ─────────────────────────────────────────
    if (showCashDropDialog) {
        CashDropDeclarationDialog(
            shift = activeShift,
            currentDrawerCash = currentDrawerCash,
            isSubmitting = isSubmittingDrop,
            serverErrorMessage = dropErrorMessage,
            onConfirmDrop = { amount, witnessId, witnessPin, denoms, notes ->
                if (isSubmittingDrop) return@CashDropDeclarationDialog
                isSubmittingDrop = true
                dropErrorMessage = null
                coroutineScope.launch {
                    val request = CashDropRequest(
                        shiftId = activeShift.id,
                        amount = amount,
                        cashierId = activeShift.cashierId.toString(),
                        witnessId = witnessId,
                        witnessPin = witnessPin,
                        denominations = denoms,
                        notes = notes.ifBlank { null }
                    )
                    when (val result = shiftRepository.recordCashDrop(request)) {
                        is ApiResult.Loading -> {
                            isSubmittingDrop = true
                        }
                        is ApiResult.Success -> {
                            currentDrawerCash -= result.data.amount
                            val noteSuffix = if (!result.data.notes.isNullOrBlank()) " Notes: ${result.data.notes}" else ""
                            dropResult = java.lang.String.format(
                                java.util.Locale.US,
                                "Transferred $%.2f to Ship Safe (Receipt: %s, Witness: %s).%s",
                                result.data.amount,
                                result.data.receiptNumber,
                                result.data.witnessId,
                                noteSuffix
                            )
                            isSubmittingDrop = false
                            dropErrorMessage = null
                            showCashDropDialog = false
                        }
                        is ApiResult.Error -> {
                            dropErrorMessage = result.message
                            isSubmittingDrop = false
                        }
                    }
                }
            },
            onDismiss = {
                if (!isSubmittingDrop) {
                    dropErrorMessage = null
                    showCashDropDialog = false
                }
            }
        )
    }

    // ── Supervisor Auth Modal ────────────────────────────────────────────────
    if (showSupervisorModal) {
        SupervisorAuthModal(
            onDismiss = { showSupervisorModal = false },
            onAuthenticated = { managerId, method ->
                authResult = "Manager: $managerId (${method.name})"
                showSupervisorModal = false
            }
        )
    }
}
