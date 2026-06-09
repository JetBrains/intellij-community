// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Base64

object PreviewEncodingUtil {
  private val contentCharset: Charset
    get() = Charsets.UTF_8

  fun encodeContent(content: String): String {
    val encoder = Base64.getEncoder()
    val bytes = encoder.encode(content.toByteArray(contentCharset))
    return bytes.toString(contentCharset)
  }

  fun decodeContent(content: String): String {
    val decoder = Base64.getDecoder()
    val bytes = decoder.decode(content)
    return bytes.toString(contentCharset)
  }

  fun encodeUrl(url: String): String {
    return URLEncoder.encode(url, contentCharset).replace("+", "%20")
  }
}
