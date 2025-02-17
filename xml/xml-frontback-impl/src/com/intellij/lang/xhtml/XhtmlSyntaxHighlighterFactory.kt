// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xhtml

import com.intellij.ide.highlighter.XmlFileHighlighter
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter

class XhtmlSyntaxHighlighterFactory :
  SingleLazyInstanceSyntaxHighlighterFactory() {

  override fun createHighlighter(): SyntaxHighlighter =
    XmlFileHighlighter(false, true)
}
