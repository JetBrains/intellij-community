// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import org.w3c.dom.Node
import org.w3c.dom.Text
import java.time.Duration

fun waitFor(
  duration: Duration = Duration.ofSeconds(5),
  interval: Duration = Duration.ofSeconds(2),
  errorMessage: String = "",
  condition: () -> Boolean
) {
  val endTime = System.currentTimeMillis() + duration.toMillis()
  var now = System.currentTimeMillis()
  while (now < endTime && condition().not()) {
    Thread.sleep(interval.toMillis())
    now = System.currentTimeMillis()
  }
  if (condition().not()) {
    throw IllegalStateException("Timeout($duration): $errorMessage")
  }
}

internal class LruCache<K, V>(private val maxEntries: Int = 1000) : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
    return this.size > maxEntries
  }
}

fun sanitizeXmlContent(node: Node) {
  node.attributes?.let { attrs ->
    (0..attrs.length).mapNotNull { attrs.item(it) }
      .forEach {
        it.textContent = sanitizeXmlChars(it.textContent)
      }
  }
  if (node is Text) {
    node.textContent = sanitizeXmlChars(node.textContent)
  }
  (0..node.childNodes.length).mapNotNull { node.childNodes.item(it) }
    .forEach { sanitizeXmlContent(it) }
}

fun sanitizeXmlChars(xml: String): String {
  if (xml.isEmpty()) return ""
  // ref : http://www.w3.org/TR/REC-xml/#charsets
  val xmlInvalidChars =
    "[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\\x{10000}-\\x{10FFFF}]".toRegex()
  return xmlInvalidChars.replace(xml, "")
}
