// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer

import com.intellij.psi.tree.IElementType

internal fun BaseHtmlLexer.isAttributeEmbedmentToken(tokenType: IElementType, attributeName: CharSequence): Boolean =
  this.isAttributeEmbedmentToken(tokenType, attributeName)

internal fun BaseHtmlLexer.isTagEmbedmentStartToken(tokenType: IElementType, tagName: CharSequence): Boolean =
  this.isTagEmbedmentStartToken(tokenType, tagName)