// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.python

import com.intellij.grazie.grammar.strategy.BaseGrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.grammar.strategy.StrategyUtils
import com.intellij.grazie.grammar.strategy.impl.RuleGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PyTokenTypes.FSTRING_TEXT
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression

internal class PythonGrammarCheckingStrategy : BaseGrammarCheckingStrategy {
  override fun isMyContextRoot(element: PsiElement) = element is PsiCommentImpl || element.elementType in PyTokenTypes.STRING_NODES

  override fun getContextRootTextDomain(root: PsiElement) = when (root.elementType) {
    PyTokenTypes.DOCSTRING -> GrammarCheckingStrategy.TextDomain.DOCS
    PyTokenTypes.END_OF_LINE_COMMENT -> GrammarCheckingStrategy.TextDomain.COMMENTS
    in PyTokenTypes.STRING_NODES -> GrammarCheckingStrategy.TextDomain.LITERALS
    else -> GrammarCheckingStrategy.TextDomain.NON_TEXT
  }

  override fun isAbsorb(element: PsiElement): Boolean {
    if (element.parent is PyFormattedStringElement) {
      return element !is LeafPsiElement || element.elementType != FSTRING_TEXT
    }

    return false
  }

  override fun getIgnoredRuleGroup(root: PsiElement, child: PsiElement) = if (root is PyStringLiteralExpression) {
    RuleGroup.LITERALS
  }
  else null

  override fun getStealthyRanges(root: PsiElement, text: CharSequence) = when (root) {
    is PsiCommentImpl -> StrategyUtils.indentIndexes(text, setOf(' ', '\t', '#'))
    else -> StrategyUtils.indentIndexes(text, setOf(' ', '\t'))
  }
}
