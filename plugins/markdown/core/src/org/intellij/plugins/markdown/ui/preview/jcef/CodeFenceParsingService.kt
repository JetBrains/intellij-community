// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.jcef

import org.intellij.markdown.ast.ASTNode

interface CodeFenceParsingService {
  fun altHighlighterAvailable(): Boolean
  fun parseToHighlightedHtml(language: String, content: String, node: ASTNode): String?
}
