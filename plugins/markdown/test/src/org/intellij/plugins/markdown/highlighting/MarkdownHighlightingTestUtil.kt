// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes

internal fun HighlightInfo.hasTextAttributesKey(key: TextAttributesKey): Boolean {
  if (forcedTextAttributesKey == key) return true
  return (forcedTextAttributes as? LayeredTextAttributes)?.keys?.contains(key) == true
}
