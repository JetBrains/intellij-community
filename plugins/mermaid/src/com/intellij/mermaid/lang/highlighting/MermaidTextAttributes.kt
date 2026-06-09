// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object MermaidTextAttributes {
  val keyword = createTextAttributesKey("MERMAID_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
  val diagram_name = createTextAttributesKey("MERMAID_DIAGRAM_NAME", keyword)
  val string = createTextAttributesKey("MERMAID_STRING", DefaultLanguageHighlighterColors.STRING)
  val comment = createTextAttributesKey("MERMAID_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
  val identifier = createTextAttributesKey("MERMAID_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
  val constant = createTextAttributesKey("MERMAID_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
  val operator = createTextAttributesKey("MERMAID_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
  val edge = createTextAttributesKey("MERMAID_EDGE", operator)
  val note = createTextAttributesKey("MERMAID_NOTE", string)
  val generic = createTextAttributesKey("MERMAID_GENERIC", DefaultLanguageHighlighterColors.IDENTIFIER)
  val title = createTextAttributesKey("MERMAID_TITLE", string)
  val frontmatter_delimiter = createTextAttributesKey("MERMAID_FRONTMATTER_DELIMITER", string)
}
