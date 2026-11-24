package com.example.core

import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String) {
    AR("ar"),
    EN("en");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: AR
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.AR }

fun t(lang: AppLanguage, ar: String, en: String): String =
    if (lang == AppLanguage.AR) ar else en
