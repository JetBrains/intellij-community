// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentUtil

internal data class Replacement(val range: TextRange, val str: CharSequence) {
  fun apply(document: Document) {
    document.replaceString(range.startOffset, range.endOffset, str)
  }

  companion object {
    // assume replacements don't intersect strictly and are sorted by ranges
    internal fun List<Replacement>.replaceSafelyIn(document: Document) {
      this.asReversed().forEach { it.apply(document) }
    }

    // assume replacements don't intersect strictly and are sorted by ranges
    internal fun List<Replacement>.replaceAllInBulk(document: Document) {
      DocumentUtil.executeInBulk(document) {
        this.replaceSafelyIn(document)
      }
    }
  }
}
