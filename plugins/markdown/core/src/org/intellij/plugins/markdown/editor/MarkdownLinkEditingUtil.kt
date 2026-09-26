// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.util.Urls

internal object MarkdownLinkEditingUtil {
  fun getLinkDestination(text: String): String? {
    val candidate = text.trim()
    if (candidate.isEmpty() || candidate.any { it.isWhitespace() }) {
      return null
    }
    return candidate.takeIf { Urls.parse(it, asLocalIfNoScheme = false) != null }
  }

  fun createInlineLink(linkText: String, linkDestination: String): String {
    return "[$linkText]($linkDestination)"
  }
}
