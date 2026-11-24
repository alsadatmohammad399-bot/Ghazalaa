package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.WebhookClient
import com.example.core.WebhookResult
import com.example.data.AiMenuRepository
import com.example.data.GhazaloRepository
import com.example.model.AiExtractedDish
import com.example.model.CartItem
import com.example.model.ChatMessage
import com.example.model.InventoryItem
import com.example.model.MenuItem
import com.example.model.RestaurantOrder
import com.example.model.RestaurantProfile
import com.example.model.StaffMember
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.ai.type.content
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed interface AiExtractState {
    data object Idle : AiExtractState
    data object Loading : AiExtractState
    data class Success(val summary: String, val dishes: List<AiExtractedDish>) : AiExtractState
    data class Error(val message: String) : AiExtractState
}

@OptIn(ExperimentalCoroutinesApi::class)
class GhazaloViewModel(
    private val repository: GhazaloRepository,
    private val aiRepository: AiMenuRepository,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String,
    initialPhone: String,
    initialLang: AppLanguage
) : ViewModel() {

    companion object {
        private const val TAG = "GhazaloViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
    }

    val isPlatformAdmin: Boolean = repository.isPlatformAdmin()

    private val _selectedRestaurantId = MutableStateFlow<String?>(null)
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _newOrderSoundEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val newOrderSoundEvents: SharedFlow<String> = _newOrderSoundEvents.asSharedFlow()
    private var knownNewOrderIds: Set<String> = emptySet()
    private var ordersInitialized = false

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _aiExtractState = MutableStateFlow<AiExtractState>(AiExtractState.Idle)
    val aiExtractState: StateFlow<AiExtractState> = _aiExtractState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    private val _isChatStreaming = MutableStateFlow(false)
    val isChatStreaming: StateFlow<Boolean> = _isChatStreaming.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _webhookResult = MutableStateFlow<WebhookResult?>(null)
    val webhookResult: StateFlow<WebhookResult?> = _webhookResult.asStateFlow()
    private val _isWebhookBusy = MutableStateFlow(false)
    val isWebhookBusy: StateFlow<Boolean> = _isWebhookBusy.asStateFlow()

    private fun <T> Flow<T>.toUiState(defaultError: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, defaultError, root)
                emit(UiState.Error(root.message ?: defaultError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .toUiState("Failed to load profile")

    val restaurantsState: StateFlow<UiState<List<RestaurantProfile>>> =
        repository.observeOwnedRestaurants(currentUserId)
            .toUiState("Failed to load restaurants")

    val allAdminRestaurantsState: StateFlow<UiState<List<RestaurantProfile>>> =
        if (isPlatformAdmin) {
            repository.observeAllRestaurantsForAdmin().toUiState("Failed to load admin restaurants")
        } else {
            MutableStateFlow(UiState.Success(emptyList()))
        }

    val activeRestaurant: StateFlow<RestaurantProfile?> =
        combine(restaurantsState, _selectedRestaurantId) { state, selectedId ->
            val list = (state as? UiState.Success)?.data.orEmpty()
            list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val menuState: StateFlow<UiState<List<MenuItem>>> =
        activeRestaurant.flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else repository.observeMenuItems(restaurant.id, restaurant.ownerId)
        }.toUiState("Failed to load menu")

    val ordersState: StateFlow<UiState<List<RestaurantOrder>>> =
        activeRestaurant.flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else repository.observeOrders(restaurant.id, restaurant.ownerId).map { list ->
                val newIds = list.filter { it.status == "NEW" }.map { it.id }.toSet()
                if (ordersInitialized) {
                    val arrived = newIds - knownNewOrderIds
                    if (arrived.isNotEmpty() && restaurant.soundAlertEnabled) {
                        val newest = list.firstOrNull { it.id in arrived }
                        _newOrderSoundEvents.tryEmit(newest?.orderNumber ?: "NEW")
                    }
                }
                knownNewOrderIds = newIds
                ordersInitialized = true
                list
            }
        }.toUiState("Failed to load orders")

    val inventoryState: StateFlow<UiState<List<InventoryItem>>> =
        activeRestaurant.flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else repository.observeInventory(restaurant.id, restaurant.ownerId)
        }.toUiState("Failed to load inventory")

    val staffState: StateFlow<UiState<List<StaffMember>>> =
        activeRestaurant.flatMapLatest { restaurant ->
            if (restaurant == null) flowOf(emptyList())
            else repository.observeStaff(restaurant.id, restaurant.ownerId)
        }.toUiState("Failed to load staff")

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(
                displayName = currentUserName,
                email = currentUserEmail,
                preferredLanguage = initialLang.code
            )
            if (initialPhone.isNotBlank()) {
                repository.updateUserPhone(initialPhone, verified = false)
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun selectRestaurant(restaurantId: String) {
        ordersInitialized = false
        knownNewOrderIds = emptySet()
        _selectedRestaurantId.value = restaurantId
    }

    fun createRestaurant(nameAr: String, nameEn: String, phone: String, loadStarter: Boolean) {
        viewModelScope.launch {
            val res = repository.createDefaultRestaurant(
                nameAr = nameAr,
                nameEn = nameEn,
                phone = phone,
                phoneVerified = false
            )
            res.onSuccess { newId ->
                _selectedRestaurantId.value = newId
                if (loadStarter) {
                    repository.seedOrientalRoyalStarterMenu(newId)
                }
                _statusBanner.value = "Restaurant created with 7-day free trial!"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to create restaurant"
            }
        }
    }

    fun loadStarterTemplateDishes() {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.seedOrientalRoyalStarterMenu(restId)
                .onSuccess { _statusBanner.value = "Starter dishes & inventory loaded." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to load template" }
        }
    }

    fun updateTemplate(templateId: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.updateRestaurantSettings(restId, mapOf("templateId" to templateId))
                .onSuccess { _statusBanner.value = "Storefront template updated!" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update template" }
        }
    }

    fun updateRestaurantSettings(updates: Map<String, Any>, successMsg: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.updateRestaurantSettings(restId, updates)
                .onSuccess { _statusBanner.value = successMsg }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun verifyPhoneNumber(phone: String, code: String, expectedCode: String) {
        viewModelScope.launch {
            if (code.trim() != expectedCode) {
                _statusBanner.value = "Invalid OTP code"
                return@launch
            }
            repository.updateUserPhone(phone, verified = true)
            activeRestaurant.value?.let { rest ->
                repository.updateRestaurantSettings(
                    rest.id,
                    mapOf("phone" to phone.trim(), "phoneVerified" to true)
                )
            }
            _statusBanner.value = "Phone verified & linked!"
        }
    }

    // --- MENU CRUD ---

    fun addMenuItem(item: MenuItem) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.addMenuItem(restId, item)
                .onSuccess { _statusBanner.value = "Dish added to menu" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to add dish" }
        }
    }

    fun toggleDishAvailability(item: MenuItem) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.toggleMenuItemAvailability(restId, item.id, !item.available)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update dish" }
        }
    }

    fun deleteMenuItem(itemId: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.deleteMenuItem(restId, itemId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete dish" }
        }
    }

    // --- CART & CHECKOUT ---

    fun addToCart(item: MenuItem) {
        val current = _cart.value.toMutableList()
        val idx = current.indexOfFirst { it.menuItem.id == item.id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(quantity = current[idx].quantity + 1)
        } else {
            current.add(CartItem(menuItem = item, quantity = 1))
        }
        _cart.value = current
    }

    fun updateCartQty(itemId: String, delta: Int) {
        val current = _cart.value.toMutableList()
        val idx = current.indexOfFirst { it.menuItem.id == itemId }
        if (idx >= 0) {
            val nextQty = current[idx].quantity + delta
            if (nextQty <= 0) current.removeAt(idx)
            else current[idx] = current[idx].copy(quantity = nextQty)
        }
        _cart.value = current
    }

    fun placeOrderFromCart(
        customerName: String,
        customerPhone: String,
        orderType: String,
        paymentMethod: String,
        notes: String
    ) {
        val rest = activeRestaurant.value ?: return
        val cartItems = _cart.value
        if (cartItems.isEmpty()) return
        val summary = cartItems.joinToString(", ") { "${it.quantity}x ${it.menuItem.nameAr}" }
        val count = cartItems.sumOf { it.quantity }.toLong().coerceAtLeast(1L)
        val total = cartItems.sumOf { it.menuItem.price * it.quantity }
        val orderNum = "GHZ-${(1000..9999).random()}"

        viewModelScope.launch {
            val res = repository.placeOrder(
                rest.id,
                RestaurantOrder(
                    orderNumber = orderNum,
                    customerName = customerName.ifBlank { "عميل المتجر" },
                    customerPhone = customerPhone,
                    orderType = orderType,
                    paymentMethod = paymentMethod,
                    itemsSummary = summary,
                    itemCount = count,
                    totalAmount = total,
                    notes = notes
                )
            )
            res.onSuccess {
                _cart.value = emptyList()
                _statusBanner.value = "Order $orderNum sent to Cashier!"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to place order"
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.updateOrderStatus(restId, orderId, newStatus)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update order" }
        }
    }

    // --- INVENTORY & STAFF ---

    fun addInventoryItem(item: InventoryItem) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.addInventoryItem(restId, item)
                .onSuccess { _statusBanner.value = "Stock item saved" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save stock item" }
        }
    }

    fun adjustInventoryStock(item: InventoryItem, delta: Double) {
        val restId = activeRestaurant.value?.id ?: return
        val next = (item.currentStock + delta).coerceAtLeast(0.0)
        viewModelScope.launch {
            repository.updateInventoryStock(restId, item.id, next)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Stock update failed" }
        }
    }

    fun addStaffMember(member: StaffMember) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.addStaffMember(restId, member)
                .onSuccess { _statusBanner.value = "Staff member added" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to add staff" }
        }
    }

    fun updateStaffPermissions(
        staff: StaffMember,
        active: Boolean = staff.active,
        canEditMenu: Boolean = staff.canEditMenu,
        canAccessCashier: Boolean = staff.canAccessCashier,
        canViewReports: Boolean = staff.canViewReports,
        canManageInventory: Boolean = staff.canManageInventory
    ) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.updateStaffMemberPermissions(
                restId, staff.id, active, canEditMenu, canAccessCashier, canViewReports, canManageInventory
            ).onFailure { _statusBanner.value = it.localizedMessage ?: "Failed updating permissions" }
        }
    }

    fun deleteStaffMember(staffId: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.deleteStaffMember(restId, staffId)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed deleting staff" }
        }
    }

    // --- SUBSCRIPTIONS & ADMIN ---

    fun submitSubscriptionPayment(planTier: String, paymentReference: String) {
        val restId = activeRestaurant.value?.id ?: return
        viewModelScope.launch {
            repository.submitSubscriptionPayment(restId, planTier, paymentReference)
                .onSuccess { _statusBanner.value = "Payment submitted! Admin will activate shortly." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Submission failed" }
        }
    }

    fun adminSetRestaurantStatus(
        restaurantId: String,
        status: String,
        planTier: String,
        trialDays: Long
    ) {
        viewModelScope.launch {
            repository.adminUpdateSubscription(restaurantId, status, planTier, trialDays)
                .onSuccess { _statusBanner.value = "Account status updated to $status" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Admin update failed" }
        }
    }

    // --- AI MENU PHOTO EXTRACTOR & STREAMING CHAT ---

    fun extractMenuFromUri(context: Context, uri: Uri, language: AppLanguage) {
        viewModelScope.launch {
            _aiExtractState.value = AiExtractState.Loading
            try {
                val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                // Downscale large photos to stay well under 20MB inline media limit
                val scaled = downscaleBitmap(rawBitmap, maxDim = 1400)
                val result = aiRepository.extractMenuFromImage(scaled, language)
                result.onSuccess { (summary, dishes) ->
                    _aiExtractState.value = AiExtractState.Success(summary, dishes)
                }.onFailure { err ->
                    _aiExtractState.value = AiExtractState.Error(err.localizedMessage ?: "AI extraction failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed decoding image for AI", e)
                _aiExtractState.value = AiExtractState.Error(e.localizedMessage ?: "Could not read selected image")
            }
        }
    }

    private fun downscaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    fun toggleExtractedDish(index: Int) {
        val current = _aiExtractState.value as? AiExtractState.Success ?: return
        val updated = current.dishes.mapIndexed { idx, dish ->
            if (idx == index) dish.copy(selected = !dish.selected) else dish
        }
        _aiExtractState.value = current.copy(dishes = updated)
    }

    fun importExtractedDishes() {
        val current = _aiExtractState.value as? AiExtractState.Success ?: return
        val restId = activeRestaurant.value?.id ?: return
        val selected = current.dishes.filter { it.selected }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            for (d in selected) {
                val res = repository.addMenuItem(
                    restId,
                    MenuItem(
                        nameAr = d.nameAr,
                        nameEn = d.nameEn,
                        categoryAr = d.categoryAr,
                        categoryEn = d.categoryEn,
                        descriptionAr = d.descriptionAr,
                        descriptionEn = d.descriptionEn,
                        price = d.price,
                        ingredients = d.ingredients,
                        imageKey = "shawarma",
                        prepMinutes = d.prepMinutes.toLong()
                    )
                )
                if (res.isSuccess) count++
            }
            _aiExtractState.value = AiExtractState.Idle
            _statusBanner.value = "Imported $count AI-extracted dishes to menu!"
        }
    }

    fun sendAiChatMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isChatStreaming.value) return
        val rest = activeRestaurant.value
        val menuCount = (menuState.value as? UiState.Success)?.data?.size ?: 0
        val ordersCount = (ordersState.value as? UiState.Success)?.data?.size ?: 0
        val lowStockCount = (inventoryState.value as? UiState.Success)?.data
            ?.count { it.currentStock <= it.minThreshold } ?: 0

        val contextNote = "Restaurant: ${rest?.nameEn ?: "GHAZALO"}, Menu items: $menuCount, Orders: $ordersCount, Low stock alerts: $lowStockCount."
        val history = buildList {
            add(content("user") { text("Context for my restaurant: $contextNote") })
            add(content("model") { text("Understood. I have your live GHAZALO metrics ready.") })
            _chatMessages.value.takeLast(8).forEach { msg ->
                add(content(if (msg.isUser) "user" else "model") { text(msg.text) })
            }
        }

        val userMsg = ChatMessage(id = "u_${System.currentTimeMillis()}", isUser = true, text = trimmed)
        val assistantId = "a_${System.currentTimeMillis() + 1}"
        _chatMessages.value = _chatMessages.value + userMsg + ChatMessage(id = assistantId, isUser = false, text = "")
        _chatError.value = null
        _isChatStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepository.streamChatReply(history, trimmed).collect { accumulated ->
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == assistantId) it.copy(text = accumulated) else it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI chat error", e)
                _chatError.value = e.localizedMessage ?: "AI chat failed"
                _chatMessages.value = _chatMessages.value.filterNot { it.id == assistantId && it.text.isBlank() }
            } finally {
                _isChatStreaming.value = false
            }
        }
    }

    // --- WEBHOOK & VERCEL API SYNC ---

    fun testVercelUrl(url: String) {
        viewModelScope.launch {
            _isWebhookBusy.value = true
            _webhookResult.value = WebhookClient.pingUrl(url)
            _isWebhookBusy.value = false
        }
    }

    fun triggerMenuWebhookSync(webhookUrl: String) {
        val rest = activeRestaurant.value ?: return
        val menuCount = (menuState.value as? UiState.Success)?.data?.size ?: 0
        viewModelScope.launch {
            _isWebhookBusy.value = true
            val payload = JSONObject().apply {
                put("event", "ghazalo.menu.sync")
                put("restaurantId", rest.id)
                put("slug", rest.slug)
                put("templateId", rest.templateId)
                put("menuCount", menuCount)
                put("timestamp", System.currentTimeMillis())
            }
            _webhookResult.value = WebhookClient.dispatchJsonWebhook(webhookUrl, payload)
            _isWebhookBusy.value = false
        }
    }
}
