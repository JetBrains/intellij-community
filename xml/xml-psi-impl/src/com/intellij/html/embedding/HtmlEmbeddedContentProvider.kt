// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType

interface HtmlEmbeddedContentProvider {
  fun handleToken(tokenType: IElementType, range: TextRange)
  fun createEmbedment(tokenType: IElementType): HtmlEmbedment?
  fun clearEmbedment()
  fun hasState(): Boolean
  fun getState(): Any?
  fun restoreState(state: Any?)
}