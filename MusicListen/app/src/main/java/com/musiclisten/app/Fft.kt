package com.musiclisten.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** In-place radix-2 FFT. Input length must be a power of two. */
object Fft {
    fun magnitude(frame: DoubleArray): DoubleArray {
        val n = frame.size
        require(n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        val re = frame.copyOf()
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val swapRe = re[i]
                re[i] = re[j]
                re[j] = swapRe
                val swapIm = im[i]
                im[i] = im[j]
                im[j] = swapIm
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenRe = cos(ang)
            val wLenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[i + k]
                    val evenIm = im[i + k]
                    val oddIndex = i + k + len / 2
                    val oddRe = re[oddIndex] * wRe - im[oddIndex] * wIm
                    val oddIm = re[oddIndex] * wIm + im[oddIndex] * wRe
                    re[i + k] = evenRe + oddRe
                    im[i + k] = evenIm + oddIm
                    re[oddIndex] = evenRe - oddRe
                    im[oddIndex] = evenIm - oddIm
                    val nextRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
        return DoubleArray(n / 2) { bin -> hypot(re[bin], im[bin]) }
    }
}
