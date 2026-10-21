// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.relaxNG.validation

import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/**
 * Creates the SAX readers that parse a RELAX NG schema.
 *
 * A reader does not load an external DTD or an external entity.
 * A reader opens an `include` or an `externalRef` only when it points to a local file.
 * Thus, a schema from an untrusted project cannot read a local file and send it out, and cannot make a network request.
 */
@ApiStatus.Internal
object LocalOnlyXmlReaderCreator : com.thaiopensource.xml.sax.XMLReaderCreator, org.kohsuke.rngom.xml.sax.XMLReaderCreator {
  override fun createXMLReader(): XMLReader {
    val factory = SAXParserFactory.newDefaultInstance()
    factory.isNamespaceAware = true
    factory.isValidating = false
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    val reader = factory.newSAXParser().xmlReader
    reader.setEntityResolver { _, systemId -> resolveEntity(systemId) }
    return reader
  }

  private fun resolveEntity(systemId: String?): InputSource? {
    if (systemId != null && isLocalUri(systemId)) {
      // the reader opens the local file itself
      return null
    }
    throw notLocalError(systemId)
  }

  @JvmStatic
  fun notLocalError(uri: String?): SAXException = SAXException("RELAX NG: a resource that is not a local file is not loaded: $uri")

  /**
   * Returns `true` when [uri] is a `file:` URI, or a `jar:` URI of a local file, without a remote host.
   * A UNC path is not local, because it opens a network connection on Windows.
   */
  @JvmStatic
  fun isLocalUri(uri: String): Boolean {
    var fileUri = uri
    if (fileUri.startsWith(URLUtil.JAR_PROTOCOL + ":", ignoreCase = true)) {
      val separator = fileUri.indexOf(URLUtil.JAR_SEPARATOR)
      if (separator < 0) return false
      fileUri = fileUri.substring(URLUtil.JAR_PROTOCOL.length + 1, separator)
    }
    if (!fileUri.startsWith(URLUtil.FILE_PROTOCOL + ":", ignoreCase = true)) return false

    var path = URLUtil.unescapePercentSequences(fileUri.substring(URLUtil.FILE_PROTOCOL.length + 1))
    if (path.startsWith("//")) {
      val authorityEnd = path.indexOf('/', 2)
      val authority = if (authorityEnd < 0) path.substring(2) else path.substring(2, authorityEnd)
      if (authority.isNotEmpty() && !authority.equals("localhost", ignoreCase = true)) return false
      path = if (authorityEnd < 0) "" else path.substring(authorityEnd)
    }
    return !path.startsWith("//") && !path.startsWith("\\") && !path.startsWith("/\\")
  }
}
