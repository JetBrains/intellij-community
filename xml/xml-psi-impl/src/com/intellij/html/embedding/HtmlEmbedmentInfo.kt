// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType

interface HtmlEmbedmentInfo {

  fun getElementType(): IElementType?
  fun createHighlightingLexer(): Lexer?

}