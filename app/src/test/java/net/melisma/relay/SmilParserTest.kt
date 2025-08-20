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
    
    @Test
    fun smilPresentation_toLayout_convertsCorrectly() {
        val parts = listOf(
            MessagePart(
                partId = 1L,
                messageId = 100L,
                contentType = "text/plain",
                text = "Hello",
                name = "text1.txt",
                contentId = "text1.txt",
                type = MessagePartType.TEXT,
                isAttachment = false
            ),
            MessagePart(
                partId = 2L,
                messageId = 100L,
                contentType = "image/jpeg",
                text = null,
                name = "image.jpg",
                dataPath = "/path/to/image.jpg",
                contentId = "image1.jpg",
                type = MessagePartType.IMAGE,
                isAttachment = true
            )
        )
        
        val smil = """
            <smil>
              <body>
                <par>
                  <text src="text1.txt"/>
                  <img src="image1.jpg"/>
                </par>
              </body>
            </smil>
        """.trimIndent()
        
        val pres = MessageScanner.parseSmil(smil)
        val layout = pres.toLayout(parts)
        
        assertEquals(2, layout.partOrder.size)
        assertTrue(layout.partOrder.contains(1L))
        assertTrue(layout.partOrder.contains(2L))
    }
}


