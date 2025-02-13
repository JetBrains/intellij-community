// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.BaseHtmlLexer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType

abstract class BaseHtmlEmbeddedContentProvider(protected val lexer: BaseHtmlLexer) : HtmlEmbeddedContentProvider {

  @JvmField
  internal var embedment = false

  private val myNamesEqual: (CharSequence?, CharSequence?) -> Boolean =
    if (lexer.isCaseInsensitive) StringUtil::equalsIgnoreCase else StringUtil::equals

  protected fun namesEqual(name1: CharSequence?, name2: CharSequence?): Boolean = myNamesEqual(name1, name2)

  override fun createEmbedment(tokenType: IElementType): HtmlEmbedment? =
    if (embedment && isStartOfEmbedment(tokenType)) {
      createEmbedmentInfo()?.let {
        val startOffset = lexer.delegate.tokenStart
        val endInfo = findTheEndOfEmbedment()
        HtmlEmbedment(it, TextRange(startOffset, endInfo.first), endInfo.second)
      }
    }
    else null

  override fun clearEmbedment() {
    embedment = false
  }

  protected abstract fun isStartOfEmbedment(tokenType: IElementType): Boolean
  protected abstract fun createEmbedmentInfo(): HtmlEmbedmentInfo?
  protected abstract fun findTheEndOfEmbedment(): Pair<Int, Int>

  override fun hasState(): Boolean = embedment

  override fun getState(): Any? =
    if (hasState()) BaseState(embedment) else null

  override fun restoreState(state: Any?) {
    if (state == null) {
      embedment = false
      return
    }
    embedment = (state as? BaseState)?.embedment == true
  }

  open class BaseState(val embedment: Boolean)

}