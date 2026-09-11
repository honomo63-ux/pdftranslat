package com.example.pdftranslator

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * مسئول باز کردن فایل PDF از طریق Uri انتخاب‌شده توسط کاربر
 * و استخراج متن هر صفحه به‌صورت جداگانه.
 */
object PdfTextExtractor {

    /**
     * متن تمام صفحات یک PDF را استخراج می‌کند.
     * توجه: این تابع باید روی یک ترد پس‌زمینه (IO) فراخوانی شود.
     */
    fun extractPages(context: Context, uri: Uri): List<PageContent> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("امکان باز کردن فایل انتخاب‌شده وجود ندارد.")

        val pages = mutableListOf<PageContent>()

        inputStream.use { stream ->
            PDDocument.load(stream).use { document ->
                if (document.isEncrypted) {
                    throw IllegalStateException("این فایل PDF رمزگذاری شده و پشتیبانی نمی‌شود.")
                }

                val stripper = PDFTextStripper()
                val totalPages = document.numberOfPages

                for (pageIndex in 1..totalPages) {
                    stripper.startPage = pageIndex
                    stripper.endPage = pageIndex
                    val text = stripper.getText(document).trim()
                    pages.add(PageContent(pageNumber = pageIndex, original = text))
                }
            }
        }

        return pages
    }
}
