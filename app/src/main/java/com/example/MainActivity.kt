package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.AppLanguage
import com.example.core.SoundAlertManager
import com.example.core.configureAppCheck
import com.example.core.t
import com.example.data.AiMenuRepository
import com.example.data.GhazaloRepository
import com.example.data.PreferencesStore
import com.example.model.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AnalyticsInventoryScreen
import com.example.ui.screens.CashierPosScreen
import com.example.ui.screens.HubAdminScreen
import com.example.ui.screens.StorefrontScreen
import com.example.ui.theme.GhazaloTheme
import com.example.viewmodel.GhazaloViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val ROUTE_STOREFRONT = "storefront"
private const val ROUTE_CASHIER = "cashier"
private const val ROUTE_AI_STUDIO = "ai_studio"
private const val ROUTE_ANALYTICS = "analytics"
private const val ROUTE_HUB = "hub"

private data class NavTab(
    val route: String,
    val labelAr: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_TABS = listOf(
    NavTab(ROUTE_STOREFRONT, "المتجر", "Menu", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    NavTab(ROUTE_CASHIER, "الكاشير", "Cashier", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
    NavTab(ROUTE_AI_STUDIO, "الذكاء AI", "AI Scan", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    NavTab(ROUTE_ANALYTICS, "التقارير", "Reports", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    NavTab(ROUTE_HUB, "الإدارة", "Hub", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Step 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        val prefs = PreferencesStore(applicationContext)

        setContent {
            val language by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.AR)
            val isDark by prefs.darkModeFlow.collectAsStateWithLifecycle(initialValue = false)
            val scope = rememberCoroutineScope()
            var pendingPhone by remember { mutableStateOf(GhazaloRepository.SUPPORT_PHONE) }

            val layoutDir = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                GhazaloTheme(darkTheme = isDark) {
                    GhazaloRoot(
                        language = language,
                        isDark = isDark,
                        pendingPhone = pendingPhone,
                        onPendingPhoneChange = { pendingPhone = it },
                        onToggleLanguage = {
                            val next = if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
                            scope.launch { prefs.setLanguage(next) }
                        },
                        onToggleDark = {
                            scope.launch { prefs.setDarkMode(!isDark) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GhazaloRoot(
    language: AppLanguage,
    isDark: Boolean,
    pendingPhone: String,
    onPendingPhoneChange: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleDark: () -> Unit
) {
    val context = LocalContext.current
    val hasFirebase = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    if (!hasFirebase) {
        AuthScreen(
            language = language,
            isDark = isDark,
            pendingPhone = pendingPhone,
            onPhoneChange = onPendingPhoneChange,
            onToggleLanguage = onToggleLanguage,
            onToggleDark = onToggleDark,
            onAuthSuccess = {}
        )
        return
    }
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            language = language,
            isDark = isDark,
            pendingPhone = pendingPhone,
            onPhoneChange = onPendingPhoneChange,
            onToggleLanguage = onToggleLanguage,
            onToggleDark = onToggleDark,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            val viewModel: GhazaloViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val dbResId = app.resources.getIdentifier("firestore_database_id", "string", app.packageName)
                        val databaseId = if (dbResId != 0) {
                            app.getString(dbResId)
                        } else {
                            "ai-studio-android-ghazalo-f0543066-5b68-42c7-a214-15d665107a92"
                        }
                        val db = FirebaseFirestore.getInstance(databaseId)
                        GhazaloViewModel(
                            repository = GhazaloRepository(db),
                            aiRepository = AiMenuRepository(),
                            currentUserId = user.uid,
                            currentUserEmail = user.email.orEmpty(),
                            currentUserName = user.displayName.orEmpty(),
                            initialPhone = pendingPhone,
                            initialLang = language
                        )
                    }
                }
            )

            MainGhazaloShell(
                viewModel = viewModel,
                language = language,
                isDark = isDark,
                onToggleLanguage = onToggleLanguage,
                onToggleDark = onToggleDark
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainGhazaloShell(
    viewModel: GhazaloViewModel,
    language: AppLanguage,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleDark: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val restaurantsState by viewModel.restaurantsState.collectAsStateWithLifecycle()
    val activeRestaurant by viewModel.activeRestaurant.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
    val staffState by viewModel.staffState.collectAsStateWithLifecycle()
    val allAdminRestaurants by viewModel.allAdminRestaurantsState.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val aiExtractState by viewModel.aiExtractState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatStreaming by viewModel.isChatStreaming.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val webhookResult by viewModel.webhookResult.collectAsStateWithLifecycle()
    val isWebhookBusy by viewModel.isWebhookBusy.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val newOrdersCount = ((ordersState as? UiState.Success)?.data ?: emptyList()).count { it.status == "NEW" }

    // Live Cashier Sound Alert listener
    LaunchedEffect(viewModel) {
        viewModel.newOrderSoundEvents.collect { orderNum ->
            SoundAlertManager.playNewOrderAlert(context)
            snackbarHostState.showSnackbar(
                t(language, "طلب جديد وصل للكاشير: $orderNum", "New Cashier Order Received: $orderNum")
            )
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_STOREFRONT

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GHAZALO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (activeRestaurant != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "• ${if (language == AppLanguage.AR) activeRestaurant!!.nameAr else activeRestaurant!!.nameEn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onToggleLanguage,
                            modifier = Modifier.testTag("shell_language_toggle")
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (language == AppLanguage.AR) "EN" else "عربي")
                        }
                        IconButton(
                            onClick = onToggleDark,
                            modifier = Modifier.testTag("shell_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = t(language, "تبديل المظهر", "Toggle Theme")
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_STOREFRONT) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == ROUTE_CASHIER && newOrdersCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$newOrdersCount") } }) {
                                            Icon(icon, contentDescription = tab.labelEn)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.labelEn)
                                    }
                                },
                                label = { Text(t(language, tab.labelAr, tab.labelEn), maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_STOREFRONT) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                    if (tab.route == ROUTE_CASHIER && newOrdersCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$newOrdersCount") } }) {
                                            Icon(icon, contentDescription = tab.labelEn)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = tab.labelEn)
                                    }
                                },
                                label = { Text(t(language, tab.labelAr, tab.labelEn)) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_STOREFRONT
                        ) {
                            composable(ROUTE_STOREFRONT) {
                                StorefrontScreen(
                                    language = language,
                                    restaurantsState = restaurantsState,
                                    activeRestaurant = activeRestaurant,
                                    menuState = menuState,
                                    cart = cart,
                                    onSelectRestaurant = viewModel::selectRestaurant,
                                    onCreateRestaurant = viewModel::createRestaurant,
                                    onLoadStarterMenu = viewModel::loadStarterTemplateDishes,
                                    onChangeTemplate = viewModel::updateTemplate,
                                    onAddMenuItem = viewModel::addMenuItem,
                                    onToggleAvailable = viewModel::toggleDishAvailability,
                                    onDeleteMenuItem = viewModel::deleteMenuItem,
                                    onAddToCart = viewModel::addToCart,
                                    onUpdateCartQty = viewModel::updateCartQty,
                                    onPlaceOrder = viewModel::placeOrderFromCart
                                )
                            }
                            composable(ROUTE_CASHIER) {
                                CashierPosScreen(
                                    language = language,
                                    activeRestaurant = activeRestaurant,
                                    ordersState = ordersState,
                                    onToggleSoundAlert = { enabled ->
                                        viewModel.updateRestaurantSettings(
                                            mapOf("soundAlertEnabled" to enabled),
                                            if (enabled) "Sound alerts enabled" else "Sound alerts muted"
                                        )
                                    },
                                    onUpdateOrderStatus = viewModel::updateOrderStatus
                                )
                            }
                            composable(ROUTE_AI_STUDIO) {
                                AiStudioScreen(
                                    language = language,
                                    currency = activeRestaurant?.currency ?: "EGP",
                                    extractState = aiExtractState,
                                    chatMessages = chatMessages,
                                    isChatStreaming = isChatStreaming,
                                    chatError = chatError,
                                    onPickMenuImage = { uri ->
                                        viewModel.extractMenuFromUri(context, uri, language)
                                    },
                                    onToggleDishSelection = viewModel::toggleExtractedDish,
                                    onImportDishes = viewModel::importExtractedDishes,
                                    onSendChat = viewModel::sendAiChatMessage
                                )
                            }
                            composable(ROUTE_ANALYTICS) {
                                AnalyticsInventoryScreen(
                                    language = language,
                                    activeRestaurant = activeRestaurant,
                                    ordersState = ordersState,
                                    inventoryState = inventoryState,
                                    staffState = staffState,
                                    onAddInventory = viewModel::addInventoryItem,
                                    onAdjustStock = viewModel::adjustInventoryStock,
                                    onAddStaff = viewModel::addStaffMember,
                                    onToggleStaffPermission = { staff, perm ->
                                        when (perm) {
                                            "cashier" -> viewModel.updateStaffPermissions(staff, canAccessCashier = !staff.canAccessCashier)
                                            "menu" -> viewModel.updateStaffPermissions(staff, canEditMenu = !staff.canEditMenu)
                                            "inventory" -> viewModel.updateStaffPermissions(staff, canManageInventory = !staff.canManageInventory)
                                            "reports" -> viewModel.updateStaffPermissions(staff, canViewReports = !staff.canViewReports)
                                        }
                                    },
                                    onDeleteStaff = viewModel::deleteStaffMember,
                                    onExportStatus = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                            composable(ROUTE_HUB) {
                                val userProfile = (userProfileState as? UiState.Success)?.data
                                val menuItems = (menuState as? UiState.Success)?.data.orEmpty()
                                val orders = (ordersState as? UiState.Success)?.data.orEmpty()
                                val inventory = (inventoryState as? UiState.Success)?.data.orEmpty()

                                HubAdminScreen(
                                    language = language,
                                    isPlatformAdmin = viewModel.isPlatformAdmin,
                                    userProfile = userProfile,
                                    activeRestaurant = activeRestaurant,
                                    menuItems = menuItems,
                                    orders = orders,
                                    inventory = inventory,
                                    allAdminRestaurants = allAdminRestaurants,
                                    webhookResult = webhookResult,
                                    isWebhookBusy = isWebhookBusy,
                                    onVerifyPhone = viewModel::verifyPhoneNumber,
                                    onSubmitPayment = viewModel::submitSubscriptionPayment,
                                    onSaveWebIntegrations = { vercel, github, webhook, email ->
                                        viewModel.updateRestaurantSettings(
                                            mapOf(
                                                "vercelStorefrontUrl" to vercel.trim(),
                                                "githubRepoUrl" to github.trim(),
                                                "webhookUrl" to webhook.trim(),
                                                "alertEmail" to email.trim()
                                            ),
                                            "Web & API integration saved!"
                                        )
                                    },
                                    onTestVercelUrl = viewModel::testVercelUrl,
                                    onTriggerWebhook = viewModel::triggerMenuWebhookSync,
                                    onAdminUpdateAccount = viewModel::adminSetRestaurantStatus,
                                    onSignOut = {
                                        signOut(credentialManager, scope)
                                    },
                                    onNotify = { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
