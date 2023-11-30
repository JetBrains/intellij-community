// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.performancePlugin.remotedriver.xpath

import org.w3c.dom.Document
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal fun Document.convertToHtml(): String {
  return try {
    val sw = StringWriter()
    sw.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">")
    sw.append("<script src=\"scripts.js\"></script>\n")
    sw.append("<script src=\"xpathEditor.js\"></script>\n")

    sw.append("<script src=\"updateButton.js\"></script>\n")
    sw.append("<div id=\"updateButton\"></div>\n")

    val tf = TransformerFactory.newInstance()
    val transformer: Transformer = tf.newTransformer()
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    transformer.setOutputProperty(OutputKeys.METHOD, "html")
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.transform(DOMSource(this), StreamResult(sw))
    sw.toString()
  }
  catch (ex: Exception) {
    throw RuntimeException("Error converting to String", ex)
  }
}