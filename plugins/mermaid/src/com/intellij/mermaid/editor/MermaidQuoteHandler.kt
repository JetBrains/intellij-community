// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.mermaid.lang.lexer.MermaidTokens

class MermaidQuoteHandler :
  SimpleTokenSetQuoteHandler(MermaidTokens.DOUBLE_QUOTE, MermaidTokens.BACK_QUOTE)
