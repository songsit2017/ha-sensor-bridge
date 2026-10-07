package dev.haos.nativeapp.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCode {
    /** Black-on-white regardless of the app theme, so any camera can read it. */
    fun bitmap(text: String, size: Int = 640): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) black else white }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
