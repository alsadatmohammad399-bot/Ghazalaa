package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.CsvExporter
import com.example.core.WebhookResult
import com.example.core.t
import com.example.data.GhazaloRepository
import com.example.model.InventoryItem
import com.example.model.MenuItem
import com.example.model.RestaurantOrder
import com.example.model.RestaurantProfile
import com.example.model.SubscriptionPlan
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SaffronGoldDark

@Composable
fun HubAdminScreen(
    language: AppLanguage,
    isPlatformAdmin: Boolean,
    userProfile: UserProfile?,
    activeRestaurant: RestaurantProfile?,
    menuItems: List<MenuItem>,
    orders: List<RestaurantOrder>,
    inventory: List<InventoryItem>,
    allAdminRestaurants: UiState<List<RestaurantProfile>>,
    webhookResult: WebhookResult?,
    isWebhookBusy: Boolean,
    onVerifyPhone: (phone: String, enteredCode: String, expectedCode: String) -> Unit,
    onSubmitPayment: (planTier: String, paymentRef: String) -> Unit,
    onSaveWebIntegrations: (vercelUrl: String, githubUrl: String, webhookUrl: String, alertEmail: String) -> Unit,
    onTestVercelUrl: (String) -> Unit,
    onTriggerWebhook: (String) -> Unit,
    onAdminUpdateAccount: (restaurantId: String, status: String, planTier: String, trialDays: Long) -> Unit,
    onSignOut: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var phoneInput by remember(userProfile?.phone, activeRestaurant?.phone) {
        mutableStateOf(userProfile?.phone?.ifBlank { activeRestaurant?.phone.orEmpty() } ?: GhazaloRepository.SUPPORT_PHONE)
    }
    var generatedOtp by remember { mutableStateOf<String?>(null) }
    var otpInput by remember { mutableStateOf("") }

    var selectedPlan by remember(activeRestaurant?.planTier) {
        mutableStateOf(
            if (activeRestaurant?.planTier in listOf("STARTER", "PRO", "ENTERPRISE")) activeRestaurant!!.planTier
            else "PRO"
        )
    }
    var paymentRef by remember { mutableStateOf("") }

    var vercelUrl by remember(activeRestaurant?.vercelStorefrontUrl) {
        mutableStateOf(activeRestaurant?.vercelStorefrontUrl.orEmpty())
    }
    var githubUrl by remember(activeRestaurant?.githubRepoUrl) {
        mutableStateOf(activeRestaurant?.githubRepoUrl.orEmpty())
    }
    var webhookUrl by remember(activeRestaurant?.webhookUrl) {
        mutableStateOf(activeRestaurant?.webhookUrl.orEmpty())
    }
    var alertEmail by remember(activeRestaurant?.alertEmail) {
        mutableStateOf(activeRestaurant?.alertEmail.orEmpty())
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = CsvExporter.buildBackupJson(activeRestaurant, menuItems, orders, inventory)
            CsvExporter.writeToUri(context, uri, json)
                .onSuccess { onNotify(t(language, "تم حفظ النسخة الاحتياطية السحابية JSON!", "Cloud JSON backup saved!")) }
                .onFailure { onNotify(it.localizedMessage ?: "Backup export failed") }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Phone SMS Security & Verification
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t(language, "توثيق رقم الهاتف والأمان (SMS)", "Phone SMS Security Verification"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (userProfile?.phoneVerified == true) {
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = EmeraldSuccess)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = t(language, "الرقم موثق: ${userProfile.phone}", "Verified: ${userProfile.phone}"),
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text(t(language, "رقم الهاتف", "Phone Number")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val code = (100000..999999).random().toString()
                                generatedOtp = code
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${phoneInput.trim()}")
                                    putExtra("sms_body", "GHAZALO verification code: $code")
                                }
                                runCatching { context.startActivity(smsIntent) }
                                onNotify(t(language, "كود التفعيل المرسل: $code", "Verification code dispatched: $code"))
                            },
                            modifier = Modifier.testTag("request_sms_otp_button")
                        ) {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t(language, "إرسال كود SMS", "Send SMS Code"))
                        }
                    }
                    if (generatedOtp != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it },
                                label = { Text(t(language, "أدخل الكود", "Enter OTP")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onVerifyPhone(phoneInput, otpInput, generatedOtp!!)
                                    generatedOtp = null
                                    otpInput = ""
                                }
                            ) {
                                Text(t(language, "تفعيل", "Verify"))
                            }
                        }
                    }
                }
            }
        }

        // 2. Subscription Packages & 7-Day Free Trial
        if (activeRestaurant != null) {
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = t(language, "الباقات والتجربة المجانية (7 أيام)", "Plans & 7-Day Free Trial"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = t(
                                    language,
                                    "الحالة الحالية: ${activeRestaurant.subscriptionStatus} • الباقة: ${activeRestaurant.planTier} (${activeRestaurant.trialDaysRemaining} يوم متبقي)",
                                    "Status: ${activeRestaurant.subscriptionStatus} • Plan: ${activeRestaurant.planTier} (${activeRestaurant.trialDaysRemaining} days left)"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SubscriptionPlan.STARTER, SubscriptionPlan.PRO, SubscriptionPlan.ENTERPRISE).forEach { plan ->
                                FilterChip(
                                    selected = selectedPlan == plan.id,
                                    onClick = { selectedPlan = plan.id },
                                    label = {
                                        Text("${t(language, plan.titleAr, plan.titleEn)} (${plan.priceEgp})")
                                    }
                                )
                            }
                        }

                        val chosen = SubscriptionPlan.fromId(selectedPlan)
                        chosen.featuresAr.zip(chosen.featuresEn).forEach { (ar, en) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t(language, ar, en), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            text = t(
                                language,
                                "حوّل عبر فودافون كاش أو إنستاباي على رقم ${GhazaloRepository.SUPPORT_PHONE} ثم أدخل رقم العملية للتفعيل الفوري:",
                                "Transfer via Vodafone Cash or InstaPay to ${GhazaloRepository.SUPPORT_PHONE}, then submit reference:"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = SaffronGoldDark
                        )

                        OutlinedTextField(
                            value = paymentRef,
                            onValueChange = { paymentRef = it },
                            label = { Text(t(language, "رقم مرجع التحويل / الدفع", "Payment Reference ID")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payment_reference_input")
                        )

                        Button(
                            onClick = { onSubmitPayment(selectedPlan, paymentRef) },
                            enabled = paymentRef.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().testTag("submit_payment_button")
                        ) {
                            Text(t(language, "إرسال طلب التفعيل بعد الدفع", "Submit Payment for Activation"))
                        }
                    }
                }
            }

            // 3. GitHub / Vercel Storefront & API Integration Hub
            item {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = t(language, "الربط مع Vercel و GitHub و API", "Vercel, GitHub & API Integration"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = vercelUrl,
                            onValueChange = { vercelUrl = it },
                            label = { Text(t(language, "رابط موقعك على Vercel", "Vercel Storefront URL")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = githubUrl,
                            onValueChange = { githubUrl = it },
                            label = { Text(t(language, "رابط مستودع GitHub", "GitHub Repository URL")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = { Text(t(language, "رابط Webhook API للمزامنة اللحظية", "Live Sync Webhook URL")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = alertEmail,
                            onValueChange = { alertEmail = it },
                            label = { Text(t(language, "بريد تنبيهات المخزون", "Alert Email Address")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onSaveWebIntegrations(vercelUrl, githubUrl, webhookUrl, alertEmail) },
                                modifier = Modifier.weight(1f).testTag("save_integrations_button")
                            ) {
                                Text(t(language, "حفظ الربط", "Save Config"))
                            }
                            FilledTonalButton(
                                onClick = { backupLauncher.launch("ghazalo_${activeRestaurant.slug}_backup.json") },
                                modifier = Modifier.weight(1f).testTag("cloud_backup_button")
                            ) {
                                Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(t(language, "نسخة احتياطية", "Backup JSON"))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onTestVercelUrl(vercelUrl) },
                                enabled = !isWebhookBusy && vercelUrl.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t(language, "فحص Vercel", "Ping Vercel"))
                            }
                            OutlinedButton(
                                onClick = { onTriggerWebhook(webhookUrl) },
                                enabled = !isWebhookBusy && webhookUrl.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t(language, "إرسال Webhook", "Sync Webhook"))
                            }
                        }

                        if (isWebhookBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        } else if (webhookResult != null) {
                            Surface(
                                color = if (webhookResult.success) EmeraldSuccess.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${webhookResult.message} (${webhookResult.latencyMs}ms)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // API Documentation Snippet
                        val apiEndpoint = "POST /api/ghazalo/v1/restaurants/${activeRestaurant.id}/sync"
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(apiEndpoint, style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(apiEndpoint))
                                        onNotify(t(language, "تم نسخ مسار API", "API endpoint copied"))
                                    }
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Platform Owner Admin Portal (Exclusive to alsadatmohammad399@gmail.com)
        if (isPlatformAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = t(language, "لوحة تحكم الأدمن المخصصة (تفعيل الحسابات)", "Owner Admin Portal (Account Activation)"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = t(
                                language,
                                "مساحة حصرية لإدارة تفعيل المطاعم بعد مراجعة الدفع وتمديد الباقات.",
                                "Exclusive control panel to activate paid restaurants and manage trial periods."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when (allAdminRestaurants) {
                is UiState.Loading -> item { CircularProgressIndicator() }
                is UiState.Error -> item { Text(allAdminRestaurants.message, color = MaterialTheme.colorScheme.error) }
                is UiState.Success -> {
                    items(allAdminRestaurants.data, key = { "admin_${it.id}" }) { rest ->
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${rest.nameAr} / ${rest.nameEn}", fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${rest.ownerEmail} • ${rest.phone} • ${rest.subscriptionStatus} (${rest.planTier})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (rest.paymentReference.isNotBlank()) {
                                    Text(
                                        text = t(language, "مرجع الدفع: ${rest.paymentReference}", "Payment Ref: ${rest.paymentReference}"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SaffronGoldDark
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onAdminUpdateAccount(rest.id, "ACTIVE", rest.planTier.takeIf { it != "FREE_TRIAL" } ?: "PRO", 30L) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(t(language, "تفعيل الحساب", "Activate"))
                                    }
                                    OutlinedButton(
                                        onClick = { onAdminUpdateAccount(rest.id, "TRIAL", "FREE_TRIAL", 7L) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(t(language, "7 أيام مجانية", "7d Trial"))
                                    }
                                    OutlinedButton(
                                        onClick = { onAdminUpdateAccount(rest.id, "EXPIRED", rest.planTier, 0L) }
                                    ) {
                                        Text(t(language, "إيقاف", "Suspend"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 24/7 Support & Sign-Out
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t(language, "الدعم الفني على مدار الساعة: 01015861991", "24/7 Support Line: 01015861991"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${GhazaloRepository.SUPPORT_PHONE}"))
                                runCatching { context.startActivity(dialIntent) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(t(language, "اتصال بالدعم", "Call Support"))
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("sign_out_button")
                        ) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t(language, "تسجيل الخروج", "Sign Out"))
                        }
                    }
                }
            }
        }
    }
}
