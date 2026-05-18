package dev.veeso.biangbiangui.examples.arabic

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dev.veeso.biangbiangui.config.OcrRecognizer
import dev.veeso.biangbiangui.protocols.OcrService
import dev.veeso.biangbiangui.protocols.OcrTextBox
import dev.veeso.biangbiangui.services.camera.DefaultOcrService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Arabic OCR via Tesseract4Android. ML Kit ships no Arabic script model, so
 * the Arabic example plugs this in through the library's OcrService seam.
 * Non-Arabic recognizers delegate to the built-in ML Kit default.
 */
class TesseractOcrService(context: Context) : OcrService {
    private val fallback = DefaultOcrService()
    private val appContext = context.applicationContext
    private val dataParent: File = File(appContext.filesDir, "tess")

    @Volatile private var assetCopied = false

    private suspend fun ensureTraineddata() {
        if (assetCopied) return
        withContext(Dispatchers.IO) {
            synchronized(this@TesseractOcrService) {
                if (assetCopied) return@synchronized
                dataParent.mkdirs()
                val tessdata = File(dataParent, "tessdata").apply { mkdirs() }
                val target = File(tessdata, "ara.traineddata")
                if (!target.exists()) {
                    val tmp = File(tessdata, "ara.traineddata.tmp")
                    appContext.assets.open("tessdata/ara.traineddata").use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    check(tmp.renameTo(target)) {
                        "Failed to install ara.traineddata"
                    }
                }
                assetCopied = true
            }
        }
    }

    override suspend fun recognize(
        bitmap: Bitmap?,
        recognizer: OcrRecognizer,
    ): List<OcrTextBox> {
        if (recognizer != OcrRecognizer.ARABIC || bitmap == null) {
            return fallback.recognize(bitmap, recognizer)
        }
        ensureTraineddata()
        return withContext(Dispatchers.Default) {
            // A new TessBaseAPI is created per call (model reload each time).
            // Acceptable for the 1 s live-OCR throttle in this example; a
            // production adapter should cache the API instance and call
            // api.clear() between frames instead of api.recycle().
            val api = TessBaseAPI()
            try {
                api.init(dataParent.absolutePath, "ara")
                api.setImage(bitmap)
                api.getUTF8Text() // forces recognition
                val out = mutableListOf<OcrTextBox>()
                val it = api.getResultIterator()
                if (it != null) {
                    try {
                        it.begin()
                        do {
                            val text = it.getUTF8Text(
                                TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE,
                            )
                            val r = it.getBoundingRect(
                                TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE,
                            )
                            if (!text.isNullOrBlank() && r != null) {
                                out += OcrTextBox(
                                    text.trim(),
                                    r.left,
                                    r.top,
                                    r.width(),
                                    r.height(),
                                )
                            }
                        } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
                    } finally {
                        it.delete()
                    }
                }
                out
            } finally {
                api.recycle()
            }
        }
    }
}
