package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.t
import com.example.model.ChatMessage
import com.example.viewmodel.AiExtractState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreen(
    language: AppLanguage,
    currency: String,
    extractState: AiExtractState,
    chatMessages: List<ChatMessage>,
    isChatStreaming: Boolean,
    chatError: String?,
    onPickMenuImage: (Uri) -> Unit,
    onToggleDishSelection: (Int) -> Unit,
    onImportDishes: () -> Unit,
    onSendChat: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onPickMenuImage(uri)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(t(language, "مسح المنيو بالصور AI", "AI Menu Scanner")) },
                icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                modifier = Modifier.testTag("ai_tab_scanner")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(t(language, "شات مساعد المطعم", "AI Advisor Chat")) },
                icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
                modifier = Modifier.testTag("ai_tab_chat")
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.img_ai_menu_scan),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(155.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Text(
                                text = t(
                                    language,
                                    "ارفع صورة المنيو لاستخراج الأقسام والأصناف والمكونات تلقائياً",
                                    "Upload Menu Photo to Auto-Extract Categories, Dishes & Ingredients"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t(
                                    language,
                                    "يستخدم النظام رؤية Gemini الذكية لقراءة الأسعار واستنتاج المكونات الدقيقة لكل طبق باللغتين العربية والإنجليزية.",
                                    "Powered by Gemini multimodal vision to extract bilingual dishes, prices, and culinary ingredients into structured menu items."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("upload_menu_photo_button")
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(t(language, "رفع صورة منيو (Photo Picker)", "Upload Menu Photo"))
                            }
                        }
                    }
                }

                when (extractState) {
                    is AiExtractState.Idle -> Unit
                    is AiExtractState.Loading -> {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    CircularProgressIndicator()
                                    Column {
                                        Text(
                                            text = t(language, "جاري تحليل صورة المنيو...", "Scanning menu with Gemini AI..."),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = t(language, "استخراج الأقسام والأسعار والمكونات", "Extracting categories, prices & ingredients"),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AiExtractState.Error -> {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = extractState.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                    is AiExtractState.Success -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = t(
                                        language,
                                        "تم استخراج ${extractState.dishes.size} صنف",
                                        "Extracted ${extractState.dishes.size} dishes"
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = onImportDishes,
                                    modifier = Modifier.testTag("import_extracted_dishes_button")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(t(language, "استيراد المحدد للمنيو", "Import Selected"))
                                }
                            }
                        }
                        itemsIndexed(extractState.dishes) { index, dish ->
                            Card(
                                onClick = { onToggleDishSelection(index) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = if (dish.selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = if (language == AppLanguage.AR) dish.nameAr else dish.nameEn,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (language == AppLanguage.AR) dish.categoryAr else dish.categoryEn,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${dish.price.toInt()} $currency",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (dish.ingredients.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            dish.ingredients.forEach { ing ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = ing,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            AiChatPanel(
                language = language,
                messages = chatMessages,
                isStreaming = isChatStreaming,
                error = chatError,
                onSend = onSendChat
            )
        }
    }
}

@Composable
private fun AiChatPanel(
    language: AppLanguage,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    error: String?,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val quickPrompts = if (language == AppLanguage.AR) {
        listOf(
            "اقترح عروض كومبو لزيادة مبيعات الشاورما",
            "كيف أتعامل مع الأصناف منخفضة المخزون؟",
            "حلل لي تسعير المنيو الحالي"
        )
    } else {
        listOf(
            "Suggest high-margin combo meals",
            "How should I handle low-stock alerts?",
            "Review my restaurant pricing strategy"
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickPrompts) { prompt ->
                Surface(
                    onClick = { onSend(prompt) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                            Text(
                                text = t(language, "مساعد غزالو الذكي جاهز", "GHAZALO AI Advisor Ready"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t(language, "اسأل عن هندسة المنيو، المبيعات، أو إدارة المخزون.", "Ask about menu engineering, sales trends, or inventory planning."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val align = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                    val bg = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val fg = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.86f)
                        ) {
                            Text(
                                text = msg.text.ifBlank { "..." },
                                color = fg,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(t(language, "اكتب سؤالك للذكاء الاصطناعي...", "Ask GHAZALO AI...")) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("ai_chat_input")
            )
            IconButton(
                onClick = {
                    val toSend = input
                    input = ""
                    onSend(toSend)
                },
                enabled = !isStreaming && input.isNotBlank(),
                modifier = Modifier.size(48.dp).testTag("ai_chat_send_button")
            ) {
                if (isStreaming) CircularProgressIndicator(modifier = Modifier.size(22.dp))
                else Icon(Icons.Filled.Send, contentDescription = t(language, "إرسال", "Send"), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
