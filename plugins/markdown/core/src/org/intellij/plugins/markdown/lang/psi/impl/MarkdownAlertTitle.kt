// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.psi.tree.IElementType

internal class MarkdownAlertTitle(type: IElementType, text: CharSequence) : MarkdownLeafPsiElement(type, text) {

  fun getType(): AlertType? {
    val type = text.substringAfter("[!").substringBefore("]").uppercase()
    return AlertType.entries.find { it.name == type }
  }

  enum class AlertType {
    NOTE,
    TIP,
    IMPORTANT,
    WARNING,
    CAUTION
  }
}
