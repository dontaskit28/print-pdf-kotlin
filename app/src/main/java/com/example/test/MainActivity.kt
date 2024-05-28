package com.example.test

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.test.ui.theme.TestTheme
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class MainActivity : ComponentActivity() {
    private lateinit var pdfUri: Uri

    // Launcher for the activity result to pick a PDF
    private val getPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            pdfUri = it
            doPdfPrint(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        MyButton(onClick = { onButtonClick() })
                    }
                }
            }
        }
    }

    // Button click handler
    private fun onButtonClick() {
        println("Button clicked")
        pickPdfFromGallery()
    }

    private fun pickPdfFromGallery() {
        getPdfLauncher.launch("application/pdf")
    }

    private fun doPdfPrint(uri: Uri) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAttributes = PrintAttributes.Builder()
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("resolution", "Resolution", 72, 72))
            .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
            .build()
        val printAdapter = PdfDocumentAdapter(this, uri)
        printManager.print("PDF Document", printAdapter, printAttributes)
    }


    // Custom PrintDocumentAdapter for printing the PDF
    class PdfDocumentAdapter(private val context: Context, private val uri: Uri) :
        PrintDocumentAdapter() {
        private var parcelFileDescriptor: ParcelFileDescriptor? = null
        private var totalPages: Int = 0
        private lateinit var newAttributes: PrintAttributes
        override fun onLayout(
            oldAttributes: PrintAttributes,
            newAttributes: PrintAttributes,
            cancellationSignal: android.os.CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            this.newAttributes = newAttributes
            try {
                parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                parcelFileDescriptor?.let {
                    val pdfRenderer = android.graphics.pdf.PdfRenderer(it)
                    totalPages = pdfRenderer.pageCount
                    pdfRenderer.close()
                }
                if (totalPages > 0) {
                    val info = PrintDocumentInfo.Builder("file_name.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(totalPages)
                        .build()
                    callback.onLayoutFinished(info, true)
                } else {
                    callback.onLayoutFailed("Page count calculation failed.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback.onLayoutFailed(e.message ?: "Error occurred")
            }
        }

        override fun onWrite(
            pages: Array<PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal,
            callback: WriteResultCallback
        ) {
            var input: InputStream? = null
            var output: OutputStream? = null
            try {
                destination.let {
                    input = context.contentResolver.openInputStream(uri)
                    output = FileOutputStream(it.fileDescriptor)

                    val buf = ByteArray(1024)
                    var bytesRead: Int
                    while (input?.read(buf).also { bytesRead = it ?: 0 } != -1) {
                        output?.write(buf, 0, bytesRead)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback.onWriteFailed(e.message ?: "Error occurred")
            } finally {
                try {
                    input?.close()
                    output?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun onFinish() {
            try {
                parcelFileDescriptor?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun MyButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("Click me")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TestTheme {
        MyButton(onClick = {})
    }
}
