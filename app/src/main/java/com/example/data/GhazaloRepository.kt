package com.example.data

import android.content.Context
import com.example.R
import com.example.model.InventoryItem
import com.example.model.MenuItem
import com.example.model.RestaurantOrder
import com.example.model.RestaurantProfile
import com.example.model.StaffMember
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class GhazaloRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val ADMIN_EMAIL = "alsadatmohammad399@gmail.com"
        const val SUPPORT_PHONE = "01015861991"
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun isPlatformAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    private fun <T> Flow<T>.handleSnapshotErrors(op: OperationType, path: String, expectedUid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        require(uid == userId || isPlatformAdmin())
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        preferredLanguage: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.ifBlank { "GHAZALO Partner" }.take(100),
                    "email" to email.ifBlank { "partner@ghazalo.app" }.take(160),
                    "phone" to "",
                    "phoneVerified" to false,
                    "preferredLanguage" to if (preferredLanguage == "en") "en" else "ar",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPhone(phone: String, verified: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "phone" to phone.trim().take(30),
                    "phoneVerified" to verified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- RESTAURANTS ---

    fun observeOwnedRestaurants(ownerId: String): Flow<List<RestaurantProfile>> = flow {
        val uid = requireUserId()
        val path = "restaurants"
        emitAll(
            db.collection("restaurants")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap -> snap.toObjects(RestaurantProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllRestaurantsForAdmin(): Flow<List<RestaurantProfile>> = flow {
        val uid = requireUserId()
        if (!isPlatformAdmin()) {
            emit(emptyList())
            return@flow
        }
        val path = "restaurants"
        emitAll(
            db.collection("restaurants")
                .snapshots()
                .map { snap -> snap.toObjects(RestaurantProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createDefaultRestaurant(
        nameAr: String,
        nameEn: String,
        phone: String,
        phoneVerified: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val email = auth.currentUser?.email ?: "owner@ghazalo.app"
        val docRef = db.collection("restaurants").document()
        val path = docRef.path
        return try {
            val safeSlug = nameEn.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "ghazalo-kitchen" }
                .take(60)
            val payload = mapOf(
                "ownerId" to uid,
                "ownerEmail" to email.take(160),
                "nameAr" to nameAr.trim().ifBlank { "مطعم زين الشام - غزالو" }.take(100),
                "nameEn" to nameEn.trim().ifBlank { "Zain El-Sham - GHAZALO" }.take(100),
                "slug" to safeSlug,
                "phone" to phone.trim().ifBlank { SUPPORT_PHONE }.take(30),
                "phoneVerified" to phoneVerified,
                "templateId" to "oriental_royal",
                "currency" to "EGP",
                "subscriptionStatus" to "TRIAL",
                "planTier" to "FREE_TRIAL",
                "trialDaysRemaining" to 7L,
                "vercelStorefrontUrl" to "https://ghazalo-store.vercel.app/$safeSlug",
                "githubRepoUrl" to "https://github.com/ghazalo-platform/ghazalo-storefront",
                "webhookUrl" to "",
                "alertEmail" to email.take(160),
                "autoBackupEnabled" to true,
                "soundAlertEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRestaurantSettings(
        restaurantId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val path = "restaurants/$restaurantId"
        return try {
            requireUserId()
            require(restaurantId.isNotBlank())
            val finalUpdates = updates.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("restaurants").document(restaurantId).update(finalUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun submitSubscriptionPayment(
        restaurantId: String,
        planTier: String,
        paymentReference: String
    ): Result<Unit> {
        val path = "restaurants/$restaurantId"
        return try {
            requireUserId()
            require(restaurantId.isNotBlank())
            require(paymentReference.isNotBlank())
            db.collection("restaurants").document(restaurantId).update(
                mapOf(
                    "subscriptionStatus" to "PENDING_PAYMENT",
                    "planTier" to planTier,
                    "paymentReference" to paymentReference.trim().take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateSubscription(
        restaurantId: String,
        status: String,
        planTier: String,
        trialDaysRemaining: Long
    ): Result<Unit> {
        val path = "restaurants/$restaurantId"
        return try {
            requireUserId()
            check(isPlatformAdmin()) { "Only platform admin can change subscription activation." }
            db.collection("restaurants").document(restaurantId).update(
                mapOf(
                    "subscriptionStatus" to status,
                    "planTier" to planTier,
                    "trialDaysRemaining" to trialDaysRemaining.coerceIn(0L, 365L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- MENU ITEMS ---

    fun observeMenuItems(restaurantId: String, ownerId: String): Flow<List<MenuItem>> = flow {
        val uid = requireUserId()
        val path = "restaurants/$restaurantId/menu_items"
        emitAll(
            db.collection("restaurants").document(restaurantId)
                .collection("menu_items")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap -> snap.toObjects(MenuItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun addMenuItem(restaurantId: String, item: MenuItem): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("restaurants").document(restaurantId).collection("menu_items").document()
        val path = docRef.path
        return try {
            val payload = mapOf(
                "ownerId" to uid,
                "restaurantId" to restaurantId,
                "nameAr" to item.nameAr.trim().ifBlank { item.nameEn }.take(120),
                "nameEn" to item.nameEn.trim().ifBlank { item.nameAr }.take(120),
                "categoryAr" to item.categoryAr.trim().ifBlank { "أطباق رئيسية" }.take(80),
                "categoryEn" to item.categoryEn.trim().ifBlank { "Main Dishes" }.take(80),
                "descriptionAr" to item.descriptionAr.trim().take(500),
                "descriptionEn" to item.descriptionEn.trim().take(500),
                "price" to item.price.coerceIn(0.0, 100000.0),
                "ingredients" to item.ingredients.map { it.trim() }.filter { it.isNotEmpty() }.take(25),
                "imageKey" to item.imageKey.ifBlank { "shawarma" }.take(60),
                "available" to item.available,
                "featured" to item.featured,
                "prepMinutes" to item.prepMinutes.coerceIn(0L, 240L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMenuItemAvailability(
        restaurantId: String,
        itemId: String,
        available: Boolean
    ): Result<Unit> {
        val path = "restaurants/$restaurantId/menu_items/$itemId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("menu_items").document(itemId)
                .update(
                    mapOf(
                        "available" to available,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(restaurantId: String, itemId: String): Result<Unit> {
        val path = "restaurants/$restaurantId/menu_items/$itemId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("menu_items").document(itemId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- ORDERS (CASHIER LIVE QUEUE) ---

    fun observeOrders(restaurantId: String, ownerId: String): Flow<List<RestaurantOrder>> = flow {
        val uid = requireUserId()
        val path = "restaurants/$restaurantId/orders"
        emitAll(
            db.collection("restaurants").document(restaurantId)
                .collection("orders")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RestaurantOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun placeOrder(restaurantId: String, order: RestaurantOrder): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("restaurants").document(restaurantId).collection("orders").document()
        val path = docRef.path
        return try {
            val payload = mapOf(
                "ownerId" to uid,
                "restaurantId" to restaurantId,
                "orderNumber" to order.orderNumber.ifBlank { "GHZ-${(1000..9999).random()}" }.take(40),
                "customerName" to order.customerName.trim().ifBlank { "عميل مباشر" }.take(100),
                "customerPhone" to order.customerPhone.trim().take(30),
                "orderType" to order.orderType,
                "paymentMethod" to order.paymentMethod,
                "status" to "NEW",
                "itemsSummary" to order.itemsSummary.trim().ifBlank { "طلب مطعم" }.take(1000),
                "itemCount" to order.itemCount.coerceIn(1L, 500L),
                "totalAmount" to order.totalAmount.coerceIn(0.0, 1000000.0),
                "notes" to order.notes.trim().take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(restaurantId: String, orderId: String, newStatus: String): Result<Unit> {
        val path = "restaurants/$restaurantId/orders/$orderId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- INVENTORY ---

    fun observeInventory(restaurantId: String, ownerId: String): Flow<List<InventoryItem>> = flow {
        val uid = requireUserId()
        val path = "restaurants/$restaurantId/inventory"
        emitAll(
            db.collection("restaurants").document(restaurantId)
                .collection("inventory")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap -> snap.toObjects(InventoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun addInventoryItem(restaurantId: String, item: InventoryItem): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("restaurants").document(restaurantId).collection("inventory").document()
        val path = docRef.path
        return try {
            val payload = mapOf(
                "ownerId" to uid,
                "restaurantId" to restaurantId,
                "nameAr" to item.nameAr.trim().ifBlank { item.nameEn }.take(120),
                "nameEn" to item.nameEn.trim().ifBlank { item.nameAr }.take(120),
                "unit" to item.unit.trim().ifBlank { "kg" }.take(30),
                "currentStock" to item.currentStock.coerceIn(0.0, 1000000.0),
                "minThreshold" to item.minThreshold.coerceIn(0.0, 1000000.0),
                "unitCost" to item.unitCost.coerceIn(0.0, 1000000.0),
                "supplierName" to item.supplierName.trim().take(120),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInventoryStock(
        restaurantId: String,
        invId: String,
        newStock: Double
    ): Result<Unit> {
        val path = "restaurants/$restaurantId/inventory/$invId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("inventory").document(invId)
                .update(
                    mapOf(
                        "currentStock" to newStock.coerceIn(0.0, 1000000.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- STAFF RBAC ---

    fun observeStaff(restaurantId: String, ownerId: String): Flow<List<StaffMember>> = flow {
        val uid = requireUserId()
        val path = "restaurants/$restaurantId/staff"
        emitAll(
            db.collection("restaurants").document(restaurantId)
                .collection("staff")
                .whereEqualTo("ownerId", ownerId)
                .snapshots()
                .map { snap -> snap.toObjects(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun addStaffMember(restaurantId: String, member: StaffMember): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docRef = db.collection("restaurants").document(restaurantId).collection("staff").document()
        val path = docRef.path
        return try {
            val payload = mapOf(
                "ownerId" to uid,
                "restaurantId" to restaurantId,
                "fullName" to member.fullName.trim().take(100),
                "email" to member.email.trim().take(160),
                "phone" to member.phone.trim().take(30),
                "role" to member.role,
                "active" to member.active,
                "canEditMenu" to member.canEditMenu,
                "canAccessCashier" to member.canAccessCashier,
                "canViewReports" to member.canViewReports,
                "canManageInventory" to member.canManageInventory,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStaffMemberPermissions(
        restaurantId: String,
        staffId: String,
        active: Boolean,
        canEditMenu: Boolean,
        canAccessCashier: Boolean,
        canViewReports: Boolean,
        canManageInventory: Boolean
    ): Result<Unit> {
        val path = "restaurants/$restaurantId/staff/$staffId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("staff").document(staffId)
                .update(
                    mapOf(
                        "active" to active,
                        "canEditMenu" to canEditMenu,
                        "canAccessCashier" to canAccessCashier,
                        "canViewReports" to canViewReports,
                        "canManageInventory" to canManageInventory,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStaffMember(restaurantId: String, staffId: String): Result<Unit> {
        val path = "restaurants/$restaurantId/staff/$staffId"
        return try {
            requireUserId()
            db.collection("restaurants").document(restaurantId)
                .collection("staff").document(staffId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STARTER TEMPLATE MENU SEEDING (USER-TRIGGERED ACTION) ---

    suspend fun seedOrientalRoyalStarterMenu(restaurantId: String): Result<Unit> {
        val starterItems = listOf(
            MenuItem(
                nameAr = "فتة شاورما دجاج زين الشام",
                nameEn = "Zain El-Sham Chicken Shawarma Fatteh",
                categoryAr = "وجبات الشاورما",
                categoryEn = "Shawarma Meals",
                descriptionAr = "أرز بسمتي ذهبي مع شاورما دجاج مشوية وخبز محمص وثومية كريمي",
                descriptionEn = "Golden basmati rice layered with grilled chicken shawarma, crispy bread & garlic toum",
                price = 185.0,
                ingredients = listOf("صدور دجاج متبلة", "أرز بسمتي", "ثومية سوري", "خبز شامي محمص", "سمنة بلدي"),
                imageKey = "shawarma",
                featured = true,
                prepMinutes = 14
            ),
            MenuItem(
                nameAr = "وجبة شاورما عربي دبل",
                nameEn = "Double Arabi Shawarma Meal",
                categoryAr = "وجبات الشاورما",
                categoryEn = "Shawarma Meals",
                descriptionAr = "رولين صاج محمص مقطع مع بطاطس مقرمشة ومخلل لفت وخيار",
                descriptionEn = "Double toasted saj wraps sliced with crispy fries and Syrian pickles",
                price = 165.0,
                ingredients = listOf("شاورما دجاج", "خبز صاج", "بطاطس", "ثومية", "مخلل خيار"),
                imageKey = "shawarma",
                featured = true,
                prepMinutes = 12
            ),
            MenuItem(
                nameAr = "صينية مشويات شامية فاخرة",
                nameEn = "Royal Syrian Mixed Grill Platter",
                categoryAr = "المشويات على الفحم",
                categoryEn = "Charcoal Grills",
                descriptionAr = "كباب لحم ضاني، شيش طاووق، كفتة حلبية مع طماطم مشوية وخبز فرن",
                descriptionEn = "Charcoal lamb kebab, shish tawook, Aleppo kofta with grilled tomatoes and pita",
                price = 420.0,
                ingredients = listOf("لحم ضاني مفروم", "شيش طاووق", "أرز زعفران", "سماق", "بقدونس"),
                imageKey = "grill",
                featured = true,
                prepMinutes = 25
            ),
            MenuItem(
                nameAr = "فراخ مشوية على الفحم كاملة",
                nameEn = "Whole Charcoal Grilled Chicken",
                categoryAr = "المشويات على الفحم",
                categoryEn = "Charcoal Grills",
                descriptionAr = "متبلة بتتبيلة الليمون والأعشاب الشامية مع أرز بسمتي وثومية",
                descriptionEn = "Marinated in Levantine lemon & herbs with basmati rice and garlic dip",
                price = 340.0,
                ingredients = listOf("دجاج كامل طازج", "ليمون", "بهارات شامية", "أرز بسمتي"),
                imageKey = "grill",
                featured = false,
                prepMinutes = 30
            ),
            MenuItem(
                nameAr = "حمص باللحمة والصنوبر",
                nameEn = "Hummus with Lamb & Pine Nuts",
                categoryAr = "المقبلات الشامية",
                categoryEn = "Levantine Mezze",
                descriptionAr = "حمص بالطحينة الفاخرة مع مكعبات لحم محمر وصنوبر وزيت زيتون",
                descriptionEn = "Silky tahini hummus topped with seared spiced lamb, pine nuts & olive oil",
                price = 115.0,
                ingredients = listOf("حمص مسلوق", "طحينة", "لحم مفروم", "صنوبر", "زيت زيتون"),
                imageKey = "hero",
                featured = false,
                prepMinutes = 8
            )
        )
        for (item in starterItems) {
            val res = addMenuItem(restaurantId, item)
            if (res.isFailure) return Result.failure(res.exceptionOrNull() ?: Exception("Failed seeding menu"))
        }
        val starterInventory = listOf(
            InventoryItem(nameAr = "صدور دجاج طازجة", nameEn = "Fresh Chicken Breast", unit = "kg", currentStock = 6.0, minThreshold = 10.0, unitCost = 195.0, supplierName = "المزارع الشامية"),
            InventoryItem(nameAr = "لحم ضاني وكفتة", nameEn = "Lamb & Kofta Meat", unit = "kg", currentStock = 18.0, minThreshold = 8.0, unitCost = 420.0, supplierName = "ملحمة الأصيل"),
            InventoryItem(nameAr = "خبز صاج طازج", nameEn = "Fresh Saj Bread", unit = "pcs", currentStock = 40.0, minThreshold = 50.0, unitCost = 3.5, supplierName = "مخبز دمشق"),
            InventoryItem(nameAr = "أرز بسمتي ذهبي", nameEn = "Golden Basmati Rice", unit = "kg", currentStock = 25.0, minThreshold = 10.0, unitCost = 85.0, supplierName = "الخيرات")
        )
        for (inv in starterInventory) {
            addInventoryItem(restaurantId, inv)
        }
        return Result.success(Unit)
    }
}
