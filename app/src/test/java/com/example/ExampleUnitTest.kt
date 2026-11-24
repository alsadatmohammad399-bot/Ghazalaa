package com.example

import com.example.core.AppLanguage
import com.example.core.CsvExporter
import com.example.core.t
import com.example.model.InventoryItem
import com.example.model.RestaurantOrder
import com.example.model.StoreTemplate
import com.example.model.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `sales csv export includes UTF8 BOM and order data`() {
        val csv = CsvExporter.buildSalesCsv(
            listOf(
                RestaurantOrder(
                    orderNumber = "GHZ-101",
                    customerName = "أحمد",
                    totalAmount = 250.0,
                    itemsSummary = "شاورما عربي"
                )
            )
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("GHZ-101"))
        assertTrue(csv.contains("شاورما عربي"))
    }

    @Test
    fun `inventory csv marks low stock accurately`() {
        val csv = CsvExporter.buildInventoryCsv(
            listOf(
                InventoryItem(
                    nameAr = "دجاج",
                    nameEn = "Chicken",
                    currentStock = 3.0,
                    minThreshold = 10.0,
                    unitCost = 180.0
                )
            )
        )
        assertTrue(csv.contains("LOW_STOCK"))
    }

    @Test
    fun `store template and plan fallbacks resolve safely`() {
        assertEquals(StoreTemplate.ORIENTAL_ROYAL, StoreTemplate.fromId("unknown_id"))
        assertEquals(StoreTemplate.LUXURY_GOLD, StoreTemplate.fromId("luxury_gold"))
        assertEquals(SubscriptionPlan.FREE_TRIAL, SubscriptionPlan.fromId("invalid"))
        assertEquals(SubscriptionPlan.PRO, SubscriptionPlan.fromId("PRO"))
    }

    @Test
    fun `bilingual localization returns expected text`() {
        assertEquals("مرحبا", t(AppLanguage.AR, "مرحبا", "Hello"))
        assertEquals("Hello", t(AppLanguage.EN, "مرحبا", "Hello"))
    }
}
