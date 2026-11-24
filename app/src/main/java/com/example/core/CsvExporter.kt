package com.example.core

import android.content.Context
import android.net.Uri
import com.example.model.InventoryItem
import com.example.model.MenuItem
import com.example.model.RestaurantOrder
import com.example.model.RestaurantProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object CsvExporter {

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\"", "\"\"")
        return "\"$cleaned\""
    }

    fun buildSalesCsv(orders: List<RestaurantOrder>): String {
        val sb = StringBuilder()
        // UTF-8 BOM for flawless Arabic support in Microsoft Excel
        sb.append('\uFEFF')
        sb.append("OrderNumber,CustomerName,CustomerPhone,OrderType,PaymentMethod,Status,ItemCount,TotalAmount,ItemsSummary,Notes\n")
        orders.forEach { o ->
            sb.append(
                listOf(
                    escapeCsv(o.orderNumber),
                    escapeCsv(o.customerName),
                    escapeCsv(o.customerPhone),
                    escapeCsv(o.orderType),
                    escapeCsv(o.paymentMethod),
                    escapeCsv(o.status),
                    o.itemCount.toString(),
                    o.totalAmount.toString(),
                    escapeCsv(o.itemsSummary),
                    escapeCsv(o.notes)
                ).joinToString(",")
            ).append("\n")
        }
        return sb.toString()
    }

    fun buildInventoryCsv(items: List<InventoryItem>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("NameAr,NameEn,Unit,CurrentStock,MinThreshold,UnitCost,StockValue,Status,Supplier\n")
        items.forEach { item ->
            val status = if (item.currentStock <= item.minThreshold) "LOW_STOCK" else "HEALTHY"
            val value = item.currentStock * item.unitCost
            sb.append(
                listOf(
                    escapeCsv(item.nameAr),
                    escapeCsv(item.nameEn),
                    escapeCsv(item.unit),
                    item.currentStock.toString(),
                    item.minThreshold.toString(),
                    item.unitCost.toString(),
                    value.toString(),
                    escapeCsv(status),
                    escapeCsv(item.supplierName)
                ).joinToString(",")
            ).append("\n")
        }
        return sb.toString()
    }

    fun buildBackupJson(
        restaurant: RestaurantProfile?,
        menu: List<MenuItem>,
        orders: List<RestaurantOrder>,
        inventory: List<InventoryItem>
    ): String {
        val root = JSONObject()
        root.put("platform", "GHAZALO")
        root.put("exportedAtMillis", System.currentTimeMillis())
        restaurant?.let { r ->
            root.put("restaurant", JSONObject().apply {
                put("id", r.id)
                put("nameAr", r.nameAr)
                put("nameEn", r.nameEn)
                put("slug", r.slug)
                put("templateId", r.templateId)
                put("currency", r.currency)
                put("subscriptionStatus", r.subscriptionStatus)
                put("planTier", r.planTier)
            })
        }
        root.put("menuItems", JSONArray().apply {
            menu.forEach { m ->
                put(JSONObject().apply {
                    put("nameAr", m.nameAr)
                    put("nameEn", m.nameEn)
                    put("categoryAr", m.categoryAr)
                    put("categoryEn", m.categoryEn)
                    put("price", m.price)
                    put("ingredients", JSONArray(m.ingredients))
                    put("available", m.available)
                })
            }
        })
        root.put("ordersCount", orders.size)
        root.put("inventoryCount", inventory.size)
        return root.toString(2)
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            } ?: return Result.failure(IllegalStateException("Could not open output stream"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
