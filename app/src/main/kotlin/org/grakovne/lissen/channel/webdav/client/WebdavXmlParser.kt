package org.grakovne.lissen.channel.webdav.client

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedWebdavResponse(
  val href: String,
  val isDirectory: Boolean,
  val eTag: String?,
  val lastModified: String?,
  val size: Long?,
  val mimeType: String?,
)

object WebdavXmlParser {
  fun parseMultistatus(xml: String): List<ParsedWebdavResponse> {
    val documentBuilderFactory =
      DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = true }

    val document =
      documentBuilderFactory
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))

    val responses = mutableListOf<ParsedWebdavResponse>()
    val responseNodes = document.getElementsByTagNameNS("*", "response")

    for (index in 0 until responseNodes.length) {
      val response = responseNodes.item(index) as? Element ?: continue
      val href =
        response
          .getElementsByTagNameNS("*", "href")
          .item(0)
          ?.textContent
          ?.trim()
          ?: continue

      val prop = response.getElementsByTagNameNS("*", "prop").item(0) as? Element
      val resourceType = prop?.getElementsByTagNameNS("*", "resourcetype")?.item(0) as? Element

      val isDirectory = (resourceType?.getElementsByTagNameNS("*", "collection")?.length ?: 0) > 0
      val eTag =
        prop
          ?.getElementsByTagNameNS("*", "getetag")
          ?.item(0)
          ?.textContent
          ?.trim()
      val lastModified =
        prop
          ?.getElementsByTagNameNS("*", "getlastmodified")
          ?.item(0)
          ?.textContent
          ?.trim()
      val size =
        prop
          ?.getElementsByTagNameNS("*", "getcontentlength")
          ?.item(0)
          ?.textContent
          ?.trim()
          ?.toLongOrNull()
      val mimeType =
        prop
          ?.getElementsByTagNameNS("*", "getcontenttype")
          ?.item(0)
          ?.textContent
          ?.trim()

      responses.add(
        ParsedWebdavResponse(
          href = href,
          isDirectory = isDirectory,
          eTag = eTag,
          lastModified = lastModified,
          size = size,
          mimeType = mimeType,
        ),
      )
    }

    return responses
  }
}
