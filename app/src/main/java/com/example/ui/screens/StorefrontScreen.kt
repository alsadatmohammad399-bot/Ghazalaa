package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.t
import com.example.model.CartItem
import com.example.model.MenuItem
import com.example.model.RestaurantProfile
import com.example.model.StoreTemplate
import com.example.model.UiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StorefrontScreen(
    language: AppLanguage,
    restaurantsState: UiState<List<RestaurantProfile>>,
    activeRestaurant: RestaurantProfile?,
    menuState: UiState<List<MenuItem>>,
    cart: List<CartItem>,
    onSelectRestaurant: (String) -> Unit,
    onCreateRestaurant: (nameAr: String, nameEn: String, phone: String, loadStarter: Boolean) -> Unit,
    onLoadStarterMenu: () -> Unit,
    onChangeTemplate: (String) -> Unit,
    onAddMenuItem: (MenuItem) -> Unit,
    onToggleAvailable: (MenuItem) -> Unit,
    onDeleteMenuItem: (String) -> Unit,
    onAddToCart: (MenuItem) -> Unit,
    onUpdateCartQty: (String, Int) -> Unit,
    onPlaceOrder: (customerName: String, customerPhone: String, orderType: String, paymentMethod: String, notes: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCreateRestaurantDialog by remember { mutableStateOf(false) }
    var showAddDishDialog by remember { mutableStateOf(false) }
    var showTemplatePickerSheet by remember { mutableStateOf(false) }
    var showCartSheet by remember { mutableStateOf(false) }

    val template = StoreTemplate.fromId(activeRestaurant?.templateId)
    val templateAccent = Color(template.accentHex)

    Box(modifier = Modifier.fillMaxSize()) {
        when (restaurantsState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(restaurantsState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val restaurants = restaurantsState.data
                if (restaurants.isEmpty() || activeRestaurant == null) {
                    EmptyRestaurantOnboarding(
                        language = language,
                        onCreateClick = { showCreateRestaurantDialog = true }
                    )
                } else {
                    val menuItems = (menuState as? UiState.Success)?.data.orEmpty()
                    val categories = remember(menuItems, language) {
                        menuItems.map { if (language == AppLanguage.AR) it.categoryAr else it.categoryEn }.distinct()
                    }
                    val filteredItems = remember(menuItems, searchQuery, selectedCategory, language) {
                        menuItems.filter { item ->
                            val cat = if (language == AppLanguage.AR) item.categoryAr else item.categoryEn
                            val matchesCat = selectedCategory == null || cat == selectedCategory
                            val matchesSearch = searchQuery.isBlank() ||
                                item.nameAr.contains(searchQuery, ignoreCase = true) ||
                                item.nameEn.contains(searchQuery, ignoreCase = true) ||
                                item.ingredients.any { it.contains(searchQuery, ignoreCase = true) }
                            matchesCat && matchesSearch
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (cart.isNotEmpty()) 96.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Restaurant Switcher + Template Switcher Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LazyRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(restaurants, key = { it.id }) { rest ->
                                        FilterChip(
                                            selected = rest.id == activeRestaurant.id,
                                            onClick = { onSelectRestaurant(rest.id) },
                                            label = {
                                                Text(
                                                    if (language == AppLanguage.AR) rest.nameAr else rest.nameEn,
                                                    maxLines = 1
                                                )
                                            }
                                        )
                                    }
                                    item {
                                        AssistChip(
                                            onClick = { showCreateRestaurantDialog = true },
                                            label = { Text(t(language, "+ مطعم جديد", "+ New Store")) }
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { showTemplatePickerSheet = true },
                                    modifier = Modifier.testTag("open_template_picker")
                                ) {
                                    Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(t(language, "القالب", "Template"))
                                }
                            }
                        }

                        // Zain El-Sham / Dynamic Template Hero Banner
                        item {
                            StorefrontHeroCard(
                                restaurant = activeRestaurant,
                                template = template,
                                templateAccent = templateAccent,
                                language = language,
                                onAddDishClick = { showAddDishDialog = true }
                            )
                        }

                        // Search & Category Chips
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(t(language, "ابحث عن طبق أو مكون...", "Search dish or ingredient..."))
                                },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("menu_search_input")
                            )
                        }

                        if (categories.isNotEmpty()) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedCategory == null,
                                            onClick = { selectedCategory = null },
                                            label = { Text(t(language, "الكل", "All")) }
                                        )
                                    }
                                    items(categories) { category ->
                                        FilterChip(
                                            selected = selectedCategory == category,
                                            onClick = {
                                                selectedCategory = if (selectedCategory == category) null else category
                                            },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                            }
                        }

                        if (menuState is UiState.Loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (filteredItems.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Restaurant,
                                            contentDescription = null,
                                            tint = templateAccent,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Text(
                                            text = t(language, "المنيو فارغ حالياً", "Your menu is empty"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = t(
                                                language,
                                                "أضف أطباقك يدوياً، أو استورد منيو كامل بتصويره عبر تبويب الذكاء الاصطناعي، أو حمّل قالب زين الشام الجاهز.",
                                                "Add dishes manually, scan a paper menu in AI Studio, or load our Zain El-Sham starter menu."
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = onLoadStarterMenu,
                                                modifier = Modifier.testTag("load_starter_menu_button")
                                            ) {
                                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(t(language, "تحميل قالب زين الشام", "Load Starter Menu"))
                                            }
                                            OutlinedButton(onClick = { showAddDishDialog = true }) {
                                                Text(t(language, "+ إضافة صنف", "+ Add Dish"))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredItems, key = { it.id }) { dish ->
                                DishCard(
                                    dish = dish,
                                    currency = activeRestaurant.currency,
                                    template = template,
                                    accentColor = templateAccent,
                                    language = language,
                                    onAddToCart = { onAddToCart(dish) },
                                    onToggleAvailable = { onToggleAvailable(dish) },
                                    onDelete = { onDeleteMenuItem(dish.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Cart Action Bar
        if (cart.isNotEmpty() && activeRestaurant != null) {
            val totalQty = cart.sumOf { it.quantity }
            val totalPrice = cart.sumOf { it.menuItem.price * it.quantity }
            Surface(
                color = templateAccent,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clickable { showCartSheet = true }
                    .testTag("floating_cart_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f)) {
                            Text(
                                text = "$totalQty",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = t(language, "مراجعة السلة وإتمام الطلب", "View Cart & Checkout"),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${totalPrice.toInt()} ${activeRestaurant.currency}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showCreateRestaurantDialog) {
        CreateRestaurantDialog(
            language = language,
            onDismiss = { showCreateRestaurantDialog = false },
            onConfirm = { ar, en, phone, loadStarter ->
                showCreateRestaurantDialog = false
                onCreateRestaurant(ar, en, phone, loadStarter)
            }
        )
    }

    if (showAddDishDialog) {
        AddMenuItemDialog(
            language = language,
            onDismiss = { showAddDishDialog = false },
            onConfirm = { dish ->
                showAddDishDialog = false
                onAddMenuItem(dish)
            }
        )
    }

    if (showTemplatePickerSheet && activeRestaurant != null) {
        ModalBottomSheet(onDismissRequest = { showTemplatePickerSheet = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = t(language, "اختر قالب تصميم المتجر", "Choose Storefront Template"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                StoreTemplate.entries.forEach { tpl ->
                    val isSelected = tpl.id == activeRestaurant.templateId
                    Card(
                        onClick = {
                            onChangeTemplate(tpl.id)
                            showTemplatePickerSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(tpl.accentHex).copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color(tpl.accentHex) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(tpl.accentHex))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = t(language, tpl.titleAr, tpl.titleEn),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = t(language, tpl.subtitleAr, tpl.subtitleEn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(tpl.accentHex))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showCartSheet && activeRestaurant != null) {
        CheckoutBottomSheet(
            language = language,
            currency = activeRestaurant.currency,
            cart = cart,
            onDismiss = { showCartSheet = false },
            onUpdateQty = onUpdateCartQty,
            onSubmitOrder = { name, phone, type, pay, notes ->
                showCartSheet = false
                onPlaceOrder(name, phone, type, pay, notes)
            }
        )
    }
}

@Composable
private fun EmptyRestaurantOnboarding(
    language: AppLanguage,
    onCreateClick: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_ghazalo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Text(
                    text = t(language, "أنشئ متجر مطعمك في ثوانٍ", "Launch Your Digital Restaurant"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t(
                        language,
                        "احصل على 7 أيام مجانية شاملة القوالب المتنوعة والكاشير الصوتي واستخراج المنيو بالـ AI.",
                        "Start your 7-day free trial with multi-template menus, sound-alert Cashier POS, and Gemini AI."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_first_restaurant_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t(language, "تسجيل المطعم الآن (7 أيام مجاناً)", "Register Restaurant (7-Day Free Trial)"))
                }
            }
        }
    }
}

@Composable
private fun StorefrontHeroCard(
    restaurant: RestaurantProfile,
    template: StoreTemplate,
    templateAccent: Color,
    language: AppLanguage,
    onAddDishClick: () -> Unit
) {
    val isDarkTemplate = template == StoreTemplate.LUXURY_GOLD
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(195.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isDarkTemplate) Color(0xFF1A1510) else Color.Black)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_ghazalo),
            contentDescription = restaurant.nameEn,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            templateAccent.copy(alpha = 0.25f),
                            Color(0xE0120F0D)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = templateAccent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = t(language, template.titleAr, template.titleEn),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    val statusText = when (restaurant.subscriptionStatus) {
                        "ACTIVE" -> t(language, "مفعل ✓", "Active ✓")
                        "PENDING_PAYMENT" -> t(language, "قيد التفعيل", "Pending Review")
                        "EXPIRED" -> t(language, "منتهي", "Expired")
                        else -> t(language, "تجربة ${restaurant.trialDaysRemaining} أيام", "${restaurant.trialDaysRemaining}d Free Trial")
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == AppLanguage.AR) restaurant.nameAr else restaurant.nameEn,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = t(language, "اطلب أونلاين الآن • توصيل سريع", "Order Online • Fresh & Hot"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE5DDD3)
                    )
                }
                FilledTonalButton(
                    onClick = onAddDishClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1A1205)
                    ),
                    modifier = Modifier.testTag("hero_add_dish_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t(language, "صنف جديد", "Add Dish"))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(
    dish: MenuItem,
    currency: String,
    template: StoreTemplate,
    accentColor: Color,
    language: AppLanguage,
    onAddToCart: () -> Unit,
    onToggleAvailable: () -> Unit,
    onDelete: () -> Unit
) {
    val imageRes = when (dish.imageKey) {
        "grill" -> R.drawable.img_dish_grill
        "hero" -> R.drawable.img_hero_ghazalo
        else -> R.drawable.img_dish_shawarma
    }
    val cardColor = if (template == StoreTemplate.LUXURY_GOLD) {
        Color(0xFF211B16)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val titleColor = if (template == StoreTemplate.LUXURY_GOLD) Color(0xFFFDF8F2) else MaterialTheme.colorScheme.onSurface

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = if (language == AppLanguage.AR) dish.nameAr else dish.nameEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.AR) dish.nameAr else dish.nameEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = t(language, "حذف", "Delete"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    val desc = if (language == AppLanguage.AR) dish.descriptionAr else dish.descriptionEn
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (template == StoreTemplate.LUXURY_GOLD) Color(0xFFB8AEA3) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${dish.price.toInt()} $currency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${dish.prepMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (dish.ingredients.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dish.ingredients.take(5).forEach { ing ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = ing,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dish.available,
                        onCheckedChange = { onToggleAvailable() }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (dish.available) t(language, "متاح", "Available") else t(language, "نفذ", "Sold Out"),
                        style = MaterialTheme.typography.labelMedium,
                        color = titleColor
                    )
                }

                Button(
                    onClick = onAddToCart,
                    enabled = dish.available,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                    modifier = Modifier.testTag("add_to_cart_${dish.id}")
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t(language, "أضف للسلة", "Add"))
                }
            }
        }
    }
}

@Composable
private fun CreateRestaurantDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit
) {
    var nameAr by remember { mutableStateOf("مطعم زين الشام") }
    var nameEn by remember { mutableStateOf("Zain El-Sham Grill") }
    var phone by remember { mutableStateOf("01015861991") }
    var loadStarter by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(language, "تسجيل مطعم جديد", "Register Restaurant")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    label = { Text(t(language, "اسم المطعم بالعربي", "Restaurant Name (AR)")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(t(language, "اسم المطعم بالإنجليزي", "Restaurant Name (EN)")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(t(language, "رقم الهاتف", "Phone Number")) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = loadStarter, onCheckedChange = { loadStarter = it })
                    Spacer(Modifier.width(8.dp))
                    Text(t(language, "تحميل منيو ومخزون زين الشام الجاهز", "Load starter menu & inventory"))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(nameAr, nameEn, phone, loadStarter) }) {
                Text(t(language, "إنشاء المطعم", "Create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t(language, "إلغاء", "Cancel")) }
        }
    )
}

@Composable
private fun AddMenuItemDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onConfirm: (MenuItem) -> Unit
) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var categoryAr by remember { mutableStateOf("الشاورما") }
    var categoryEn by remember { mutableStateOf("Shawarma") }
    var priceStr by remember { mutableStateOf("150") }
    var ingredientsStr by remember { mutableStateOf("دجاج, ثومية, خبز صاج") }
    var imageKey by remember { mutableStateOf("shawarma") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(language, "إضافة صنف للمنيو", "Add Menu Dish")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it },
                    label = { Text(t(language, "الاسم بالعربي", "Name (AR)")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(t(language, "الاسم بالإنجليزي", "Name (EN)")) },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categoryAr,
                        onValueChange = { categoryAr = it },
                        label = { Text(t(language, "القسم", "Category")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text(t(language, "السعر", "Price")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = ingredientsStr,
                    onValueChange = { ingredientsStr = it },
                    label = { Text(t(language, "المكونات (بفاصلة)", "Ingredients (comma-separated)")) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shawarma" to "Shawarma", "grill" to "Grill", "hero" to "Mezze").forEach { (key, label) ->
                        FilterChip(
                            selected = imageKey == key,
                            onClick = { imageKey = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val ings = ingredientsStr.split(",", "،").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(
                        MenuItem(
                            nameAr = nameAr.ifBlank { nameEn.ifBlank { "طبق جديد" } },
                            nameEn = nameEn.ifBlank { nameAr.ifBlank { "New Dish" } },
                            categoryAr = categoryAr,
                            categoryEn = categoryEn.ifBlank { categoryAr },
                            price = price,
                            ingredients = ings,
                            imageKey = imageKey
                        )
                    )
                }
            ) {
                Text(t(language, "حفظ الصنف", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t(language, "إلغاء", "Cancel")) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutBottomSheet(
    language: AppLanguage,
    currency: String,
    cart: List<CartItem>,
    onDismiss: () -> Unit,
    onUpdateQty: (String, Int) -> Unit,
    onSubmitOrder: (String, String, String, String, String) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("DELIVERY") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }
    val total = cart.sumOf { it.menuItem.price * it.quantity }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = t(language, "إتمام الطلب وإرساله للكاشير", "Checkout & Send to Cashier"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            cart.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (language == AppLanguage.AR) item.menuItem.nameAr else item.menuItem.nameEn,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onUpdateQty(item.menuItem.id, -1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }
                        Text("${item.quantity}", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onUpdateQty(item.menuItem.id, 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                    Text("${(item.menuItem.price * item.quantity).toInt()} $currency", fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text(t(language, "اسم العميل", "Customer Name")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text(t(language, "رقم الهاتف", "Phone")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("DELIVERY" to t(language, "توصيل", "Delivery"), "PICKUP" to t(language, "استلام", "Pickup"), "DINE_IN" to t(language, "صالة", "Dine-In"))) { (code, label) ->
                    FilterChip(
                        selected = orderType == code,
                        onClick = { orderType = code },
                        label = { Text(label) }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("CASH" to "Cash", "INSTAPAY" to "InstaPay", "VODAFONE_CASH" to "Vodafone Cash", "CARD" to "Card")) { (code, label) ->
                    FilterChip(
                        selected = paymentMethod == code,
                        onClick = { paymentMethod = code },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(t(language, "ملاحظات المطبخ", "Kitchen Notes")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSubmitOrder(customerName, customerPhone, orderType, paymentMethod, notes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_checkout_button")
            ) {
                Text(
                    text = t(
                        language,
                        "تأكيد الطلب • ${total.toInt()} $currency",
                        "Place Order • ${total.toInt()} $currency"
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
