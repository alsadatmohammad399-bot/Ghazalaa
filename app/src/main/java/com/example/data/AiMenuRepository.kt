package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AppLanguage
import com.example.model.AiExtractedDish
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiMenuRepository {
    companion object {
        private const val TAG = "AiMenuRepository"
        private const val MODEL_NAME = "gemini-3.8-flash"
    }

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "restaurantSummary" to Schema.string(description = "Brief summary of the scanned menu style"),
            "dishes" to Schema.array(
                description = "All menu dishes extracted from the image",
                items = Schema.obj(
                    properties = mapOf(
                        "nameAr" to Schema.string(description = "Dish name in Arabic"),
                        "nameEn" to Schema.string(description = "Dish name in English"),
                        "categoryAr" to Schema.string(description = "Category name in Arabic"),
                        "categoryEn" to Schema.string(description = "Category name in English"),
                        "descriptionAr" to Schema.string(description = "Short appetizing Arabic description"),
                        "descriptionEn" to Schema.string(description = "Short appetizing English description"),
                        "price" to Schema.double(description = "Numeric dish price"),
                        "ingredients" to Schema.array(
                            items = Schema.string(),
                            description = "List of detected or inferred culinary ingredients"
                        ),
                        "prepMinutes" to Schema.integer(description = "Estimated prep time in minutes")
                    ),
                    optionalProperties = listOf("descriptionAr", "descriptionEn", "prepMinutes")
                )
            )
        )
    )

    private val extractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    "You are GHAZALO AI, an expert restaurant operations advisor, culinary strategist, and POS/inventory analyst. " +
                        "Answer concisely and practically in the user's language (Arabic or English). Help with menu engineering, pricing, low-stock mitigation, and daily sales insights."
                )
            }
        )
    }

    suspend fun extractMenuFromImage(
        bitmap: Bitmap,
        language: AppLanguage
    ): Result<Pair<String, List<AiExtractedDish>>> {
        return try {
            val promptText = if (language == AppLanguage.AR) {
                "استخرج جميع الأقسام والأصناف والأسعار والمكونات بدقة عالية من صورة المنيو المرفقة. " +
                    "وفر اسم الصنف والقسم والوصف باللغتين العربية والإنجليزية، واستنتج المكونات الأساسية لكل طبق بدقة."
            } else {
                "Extract every category, dish name, price, and ingredient list accurately from the uploaded menu image. " +
                    "Provide bilingual Arabic and English names, categories, descriptions, and accurate culinary ingredients."
            }
            val response = withTimeout(60_000L) {
                extractionModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }
            val rawJson = response.text ?: return Result.failure(IllegalStateException("Empty AI response"))
            val root = JSONObject(rawJson)
            val summary = root.optString("restaurantSummary", "")
            val dishesArr = root.optJSONArray("dishes") ?: return Result.failure(IllegalStateException("No dishes found"))
            val parsed = buildList {
                for (i in 0 until dishesArr.length()) {
                    val obj = dishesArr.getJSONObject(i)
                    val ingArr = obj.optJSONArray("ingredients")
                    val ingredients = buildList {
                        if (ingArr != null) {
                            for (j in 0 until ingArr.length()) {
                                val ing = ingArr.optString(j).trim()
                                if (ing.isNotEmpty()) add(ing)
                            }
                        }
                    }
                    add(
                        AiExtractedDish(
                            nameAr = obj.optString("nameAr", obj.optString("nameEn", "طبق")),
                            nameEn = obj.optString("nameEn", obj.optString("nameAr", "Dish")),
                            categoryAr = obj.optString("categoryAr", "أطباق رئيسية"),
                            categoryEn = obj.optString("categoryEn", "Main Dishes"),
                            descriptionAr = obj.optString("descriptionAr", ""),
                            descriptionEn = obj.optString("descriptionEn", ""),
                            price = obj.optDouble("price", 0.0),
                            ingredients = ingredients,
                            prepMinutes = obj.optInt("prepMinutes", 15).coerceIn(1, 180),
                            selected = true
                        )
                    )
                }
            }
            Result.success(summary to parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Menu extraction timed out", e)
            Result.failure(IllegalStateException("AI menu extraction timed out after 60s. Please try a clearer photo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Menu extraction failed", e)
            Result.failure(e)
        }
    }

    fun streamChatReply(
        history: List<Content>,
        prompt: String
    ): Flow<String> = flow {
        val accumulated = StringBuilder()
        val chat = chatModel.startChat(history = history)
        withTimeout(60_000L) {
            chat.sendMessageStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI returned an empty response.")
        }
    }
}
