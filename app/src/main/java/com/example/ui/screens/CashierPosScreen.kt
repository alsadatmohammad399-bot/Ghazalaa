package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.SoundAlertManager
import com.example.core.t
import com.example.model.RestaurantOrder
import com.example.model.RestaurantProfile
import com.example.model.UiState
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SaffronGoldDark

@Composable
fun CashierPosScreen(
    language: AppLanguage,
    activeRestaurant: RestaurantProfile?,
    ordersState: UiState<List<RestaurantOrder>>,
    onToggleSoundAlert: (Boolean) -> Unit,
    onUpdateOrderStatus: (orderId: String, newStatus: String) -> Unit
) {
    val context = LocalContext.current
    var statusFilter by remember { mutableStateOf("ALL") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))

        // Cashier Sound & Live Status Header
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PointOfSale,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t(language, "شاشة الكاشير المباشرة", "Live Cashier POS"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = t(
                            language,
                            "يصدر الجهاز تنبيهاً صوتياً فور وصول أي طلب جديد",
                            "Auto audio chime & vibration on incoming orders"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val soundEnabled = activeRestaurant?.soundAlertEnabled ?: true
                    FilledTonalButton(
                        onClick = { onToggleSoundAlert(!soundEnabled) },
                        modifier = Modifier.testTag("toggle_cashier_sound")
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Button(
                        onClick = { SoundAlertManager.playNewOrderAlert(context) },
                        modifier = Modifier.testTag("test_cashier_chime_button")
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t(language, "تجربة", "Test"))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf(
                "ALL" to t(language, "الكل", "All"),
                "NEW" to t(language, "جديد", "New"),
                "PREPARING" to t(language, "تجهيز", "Preparing"),
                "READY" to t(language, "جاهز", "Ready"),
                "COMPLETED" to t(language, "مكتمل", "Completed")
            )
            items(filters) { (code, label) ->
                FilterChip(
                    selected = statusFilter == code,
                    onClick = { statusFilter = code },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (ordersState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(ordersState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val orders = ordersState.data.filter { statusFilter == "ALL" || it.status == statusFilter }
                if (orders.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = t(language, "لا توجد طلبات في هذا القسم حالياً", "No orders in this queue"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t(
                                    language,
                                    "اطلب من صفحة المنيو ليصل الطلب فوراً هنا مع رنين الكاشير.",
                                    "Place an order from the Storefront tab to trigger a live sound alert here."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(orders, key = { it.id }) { order ->
                            OrderTicketCard(
                                order = order,
                                currency = activeRestaurant?.currency ?: "EGP",
                                language = language,
                                onUpdateStatus = { next -> onUpdateOrderStatus(order.id, next) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderTicketCard(
    order: RestaurantOrder,
    currency: String,
    language: AppLanguage,
    onUpdateStatus: (String) -> Unit
) {
    val badgeColor = when (order.status) {
        "NEW" -> MaterialTheme.colorScheme.error
        "PREPARING" -> SaffronGoldDark
        "READY" -> Color(0xFF0284C7)
        "COMPLETED" -> EmeraldSuccess
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.orderNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = order.status,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = "${order.totalAmount.toInt()} $currency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "${order.customerName} • ${order.customerPhone.ifBlank { "-" }} • ${order.orderType} (${order.paymentMethod})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = order.itemsSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (order.notes.isNotBlank()) {
                Text(
                    text = t(language, "ملاحظة: ${order.notes}", "Note: ${order.notes}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SaffronGoldDark
                )
            }

            if (order.status in listOf("NEW", "PREPARING", "READY")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (order.status) {
                        "NEW" -> {
                            Button(
                                onClick = { onUpdateStatus("PREPARING") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t(language, "بدء التجهيز", "Start Preparing"))
                            }
                        }
                        "PREPARING" -> {
                            Button(
                                onClick = { onUpdateStatus("READY") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t(language, "جاهز للتسليم", "Mark Ready"))
                            }
                        }
                        "READY" -> {
                            Button(
                                onClick = { onUpdateStatus("COMPLETED") },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t(language, "تسليم وإغلاق", "Complete"))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onUpdateStatus("CANCELLED") }
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t(language, "إلغاء", "Cancel"))
                    }
                }
            }
        }
    }
}
