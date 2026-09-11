package com.example.pdftranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // راه‌اندازی اولیه PdfBox-Android (برای بارگذاری فونت‌ها و منابع لازم)
        PDFBoxResourceLoader.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PdfTranslatorScreen()
                }
            }
        }
    }
}

@Composable
fun PdfTranslatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<PageContent>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var globalError by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // برخی providerها اجازه persistable permission نمی‌دهند؛ بی‌خطر نادیده گرفته می‌شود
        }

        fileName = uri.lastPathSegment
        globalError = null
        pages = emptyList()
        isExtracting = true

        scope.launch {
            try {
                val extracted = withContext(Dispatchers.Default) {
                    PdfTextExtractor.extractPages(context, uri)
                }
                pages = extracted
                isExtracting = false
                isTranslating = true

                // ترجمه صفحه به صفحه، به‌ترتیب، و آپدیت تدریجی UI
                val updatedPages = extracted.toMutableList()
                for (i in updatedPages.indices) {
                    updatedPages[i] = updatedPages[i].copy(isTranslating = true)
                    pages = updatedPages.toList()

                    val translatedPage = TranslatorHelper.translatePage(updatedPages[i])
                    updatedPages[i] = translatedPage
                    pages = updatedPages.toList()
                }
                isTranslating = false
            } catch (e: Exception) {
                globalError = "خطا در پردازش فایل: ${e.message}"
                isExtracting = false
                isTranslating = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("مترجم PDF فارسی") })

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("انتخاب فایل PDF")
            }

            fileName?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("فایل انتخاب‌شده: $it", style = MaterialTheme.typography.bodySmall)
            }

            if (isExtracting) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("در حال استخراج متن از PDF...")
                }
            } else if (isTranslating) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("در حال ترجمه به فارسی...")
                }
            }

            globalError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(pages) { page ->
                PageCard(page)
                Spacer(modifier = Modifier.height(12.dp))
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PageCard(page: PageContent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "صفحه ${page.pageNumber}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text("متن اصلی:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            Text(
                text = page.original.ifBlank { "(متنی یافت نشد)" },
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))

            Text("ترجمه فارسی:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)

            when {
                page.error != null -> Text(page.error, color = MaterialTheme.colorScheme.error)
                page.isTranslating -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("در حال ترجمه...")
                }
                else -> Text(
                    text = page.translated.ifBlank { "—" },
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
