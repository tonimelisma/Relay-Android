package net.melisma.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmilParserTest {

    @Test
    fun parse_simpleSmil_producesSlidesAndItems() {
        val smil = """
            <smil>
              <body>
                <par>
                  <text src="text1.txt"/>
                  <img src="image1.jpg"/>
                </par>
                <par>
                  <audio src="audio1.amr"/>
                </par>
              </body>
            </smil>
        """.trimIndent()
        val pres = MessageScanner.parseSmil(smil)
        assertTrue(pres.slides.isNotEmpty())
        assertEquals(2, pres.slides.size)
        assertEquals(2, pres.slides[0].items.size)
        assertEquals("text", pres.slides[0].items[0].type)
        assertEquals("image", pres.slides[0].items[1].type)
        assertEquals("audio", pres.slides[1].items[0].type)
    }
}


