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
    private val dataParent: File =
        File(context.filesDir, "tess").apply { mkdirs() }

    init {
        val tessdata = File(dataParent, "tessdata").apply { mkdirs() }
        val target = File(tessdata, "ara.traineddata")
        if (!target.exists()) {
            context.assets.open("tessdata/ara.traineddata").use { input ->
                target.outputStream().use { input.copyTo(it) }
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
        return withContext(Dispatchers.Default) {
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
