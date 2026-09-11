package com.example.pdftranslator

/**
 * نگه‌دارنده متن اصلی و ترجمه‌شده هر صفحه از PDF
 */
data class PageContent(
    val pageNumber: Int,
    val original: String,
    val translated: String = "",
    val isTranslating: Boolean = false,
    val error: String? = null
)
