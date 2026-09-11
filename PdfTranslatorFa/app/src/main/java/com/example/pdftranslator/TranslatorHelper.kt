package com.example.pdftranslator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * مسئول تشخیص زبان مبدا و ترجمه متن هر صفحه به فارسی،
 * با استفاده از ML Kit (ترجمه روی خود دستگاه، بدون نیاز به کلید API).
 *
 * نکته: برای هر زبان مبدا جدید، ML Kit باید یک بار مدل مربوطه را
 * دانلود کند (حدود چند مگابایت)، بنابراین اتصال اینترنت در اولین
 * استفاده از هر زبان لازم است. بعد از آن ترجمه کاملاً آفلاین کار می‌کند.
 */
object TranslatorHelper {

    private const val CHUNK_SIZE = 4000 // برای جلوگیری از ارسال متن‌های خیلی بزرگ یکجا

    suspend fun translatePage(page: PageContent): PageContent {
        if (page.original.isBlank()) {
            return page.copy(translated = "(این صفحه متن قابل استخراج نداشت — ممکن است اسکن‌شده باشد)")
        }

        return try {
            val sourceLanguageTag = identifyLanguage(page.original)
            val translatedText = translateInChunks(page.original, sourceLanguageTag)
            page.copy(translated = translatedText, isTranslating = false, error = null)
        } catch (e: Exception) {
            page.copy(isTranslating = false, error = "خطا در ترجمه: ${e.message}")
        }
    }

    private suspend fun translateInChunks(text: String, sourceLanguageTag: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguageTag)
            .setTargetLanguage(TranslateLanguage.PERSIAN)
            .build()
        val translator = Translation.getClient(options)

        return try {
            downloadModelIfNeeded(translator)
            val chunks = text.chunked(CHUNK_SIZE)
            val translatedChunks = chunks.map { chunk -> translateText(translator, chunk) }
            translatedChunks.joinToString(separator = "\n")
        } finally {
            translator.close()
        }
    }

    private suspend fun identifyLanguage(text: String): String = suspendCancellableCoroutine { cont ->
        val identifier = LanguageIdentification.getClient()
        val sample = text.take(1000) // نمونه کوتاه برای تشخیص سریع‌تر
        identifier.identifyLanguage(sample)
            .addOnSuccessListener { languageCode ->
                val resolved = when {
                    languageCode == "und" -> TranslateLanguage.ENGLISH
                    TranslateLanguage.fromLanguageTag(languageCode) == null -> TranslateLanguage.ENGLISH
                    else -> TranslateLanguage.fromLanguageTag(languageCode)!!
                }
                if (cont.isActive) cont.resume(resolved)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(TranslateLanguage.ENGLISH)
            }
    }

    private suspend fun downloadModelIfNeeded(translator: Translator) = suspendCancellableCoroutine<Unit> { cont ->
        val conditions = DownloadConditions.Builder()
            .build() // اجازه دانلود روی هر نوع شبکه (وای‌فای یا موبایل دیتا)
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    private suspend fun translateText(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
}
