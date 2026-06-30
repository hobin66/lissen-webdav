package io.github.hobin66.webdavplayer.channel.webdav.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.xml.sax.SAXException

class WebdavXmlParserSecurityTest {
  @Test
  fun `parses normal multistatus responses`() {
    val parsed =
      WebdavXmlParser.parseMultistatus(
        """
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/dav/book/file.mp3</d:href>
            <d:propstat>
              <d:prop>
                <d:getetag>"abc"</d:getetag>
                <d:getcontentlength>12</d:getcontentlength>
                <d:getcontenttype>audio/mpeg</d:getcontenttype>
              </d:prop>
            </d:propstat>
          </d:response>
        </d:multistatus>
        """.trimIndent(),
      )

    assertEquals(1, parsed.size)
    assertEquals("/dav/book/file.mp3", parsed.single().href)
  }

  @Test
  fun `rejects doctypes before entity expansion`() {
    assertThrows(SAXException::class.java) {
      WebdavXmlParser.parseMultistatus(
        """
        <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>&xxe;</d:href>
          </d:response>
        </d:multistatus>
        """.trimIndent(),
      )
    }
  }
}
