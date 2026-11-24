package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class StoreTemplate(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val subtitleAr: String,
    val subtitleEn: String,
    val accentHex: Long,
    val surfaceHex: Long
) {
    ORIENTAL_ROYAL(
        id = "oriental_royal",
        titleAr = "القالب الملكي الشامي (زين الشام)",
        titleEn = "Oriental Royal (Zain El-Sham)",
        subtitleAr = "هيرو غني، تبويبات سريعة، بطاقات طلب مباشر",
        subtitleEn = "Rich culinary hero, instant category tabs, bold dish cards",
        accentHex = 0xFFDC2626,
        surfaceHex = 0xFFFEF3C7
    ),
    MODERN_BISTRO(
        id = "modern_bistro",
        titleAr = "بيسترو عصري نظيف",
        titleEn = "Modern Minimal Bistro",
        subtitleAr = "تصميم هادئ، صور واضحة، تجربة راقية",
        subtitleEn = "Editorial clean grid, airy spacing, crisp typography",
        accentHex = 0xFF0F766E,
        surfaceHex = 0xFFCCFBF1
    ),
    EXPRESS_GRID(
        id = "express_grid",
        titleAr = "شبكة الطلبات السريعة",
        titleEn = "Express Fast-Food Grid",
        subtitleAr = "أزرار إضافة فورية، مثالي للوجبات السريعة",
        subtitleEn = "High-speed 2-column cards with instant add-to-cart",
        accentHex = 0xFFEA580C,
        surfaceHex = 0xFFFFEDD5
    ),
    LUXURY_GOLD(
        id = "luxury_gold",
        titleAr = "الذهبي الفاخر (داكن)",
        titleEn = "Luxury Gold Obsidian",
        subtitleAr = "طابع ليلي ذهبي للمطاعم الراقية واللاونجات",
        subtitleEn = "Obsidian & saffron gold dining showcase",
        accentHex = 0xFFF59E0B,
        surfaceHex = 0xFF29221A
    );

    companion object {
        fun fromId(raw: String?): StoreTemplate =
            entries.firstOrNull { it.id == raw } ?: ORIENTAL_ROYAL
    }
}

enum class SubscriptionPlan(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val priceEgp: Int,
    val featuresAr: List<String>,
    val featuresEn: List<String>
) {
    FREE_TRIAL(
        id = "FREE_TRIAL",
        titleAr = "تجربة مجانية 7 أيام",
        titleEn = "7-Day Free Trial",
        priceEgp = 0,
        featuresAr = listOf("منيو إلكتروني بـ 4 قوالب", "كاشير بصوت تنبيه", "استخراج المنيو بالذكاء الاصطناعي"),
        featuresEn = listOf("4 Storefront Templates", "Sound-Alert Cashier POS", "AI Menu Scanner")
    ),
    STARTER(
        id = "STARTER",
        titleAr = "باقة البداية",
        titleEn = "Starter Plan",
        priceEgp = 350,
        featuresAr = listOf("حتى 120 صنف", "نظام طلبات إلكتروني", "تصدير تقارير إكسل"),
        featuresEn = listOf("Up to 120 dishes", "Online ordering storefront", "Excel CSV Reports")
    ),
    PRO(
        id = "PRO",
        titleAr = "الباقة الاحترافية",
        titleEn = "Pro Growth Plan",
        priceEgp = 690,
        featuresAr = listOf("أصناف غير محدودة + AI غير محدود", "مخزون وتنبيهات البريد", "صلاحيات موظفين وربط API"),
        featuresEn = listOf("Unlimited items & AI", "Inventory & email alerts", "Staff RBAC & Webhook API")
    ),
    ENTERPRISE(
        id = "ENTERPRISE",
        titleAr = "باقة المؤسسات والسلاسل",
        titleEn = "Enterprise Chain",
        priceEgp = 1290,
        featuresAr = listOf("ربط Vercel & GitHub مخصص", "نسخ احتياطي سحابي فوري", "دعم فني مباشر 24/7"),
        featuresEn = listOf("Custom Vercel & GitHub Sync", "Instant Cloud Backup", "24/7 Priority Support")
    );

    companion object {
        fun fromId(raw: String?): SubscriptionPlan =
            entries.firstOrNull { it.id == raw } ?: FREE_TRIAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val phoneVerified: Boolean = false,
    val preferredLanguage: String = "ar",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RestaurantProfile(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val nameAr: String = "مطعم زين الشام - غزالو",
    val nameEn: String = "Zain El-Sham - GHAZALO",
    val slug: String = "zain-el-sham",
    val phone: String = "01015861991",
    val phoneVerified: Boolean = false,
    val templateId: String = StoreTemplate.ORIENTAL_ROYAL.id,
    val currency: String = "EGP",
    val subscriptionStatus: String = "TRIAL",
    val planTier: String = "FREE_TRIAL",
    val trialDaysRemaining: Long = 7L,
    val paymentReference: String = "",
    val vercelStorefrontUrl: String = "",
    val githubRepoUrl: String = "",
    val webhookUrl: String = "",
    val alertEmail: String = "",
    val autoBackupEnabled: Boolean = true,
    val soundAlertEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val restaurantId: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val categoryAr: String = "",
    val categoryEn: String = "",
    val descriptionAr: String = "",
    val descriptionEn: String = "",
    val price: Double = 0.0,
    val ingredients: List<String> = emptyList(),
    val imageKey: String = "shawarma",
    val available: Boolean = true,
    val featured: Boolean = false,
    val prepMinutes: Long = 15L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RestaurantOrder(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val restaurantId: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val orderType: String = "DELIVERY",
    val paymentMethod: String = "CASH",
    val status: String = "NEW",
    val itemsSummary: String = "",
    val itemCount: Long = 1L,
    val totalAmount: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val restaurantId: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val unit: String = "kg",
    val currentStock: Double = 0.0,
    val minThreshold: Double = 5.0,
    val unitCost: Double = 0.0,
    val supplierName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val restaurantId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "CASHIER",
    val active: Boolean = true,
    val canEditMenu: Boolean = false,
    val canAccessCashier: Boolean = true,
    val canViewReports: Boolean = false,
    val canManageInventory: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int = 1,
    val notes: String = ""
)

data class AiExtractedDish(
    val nameAr: String,
    val nameEn: String,
    val categoryAr: String,
    val categoryEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val price: Double,
    val ingredients: List<String>,
    val prepMinutes: Int = 15,
    val selected: Boolean = true
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
