package com.musiclisten.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CardImageTest {
    @Test
    fun sampleSizeShrinksLargePhotos() {
        assertEquals(1, CardImage.sampleSize(800, 600))
        assertEquals(2, CardImage.sampleSize(2000, 1000))
        assertEquals(4, CardImage.sampleSize(4000, 3000))
        assertEquals(1, CardImage.sampleSize(0, 0))
    }
}
