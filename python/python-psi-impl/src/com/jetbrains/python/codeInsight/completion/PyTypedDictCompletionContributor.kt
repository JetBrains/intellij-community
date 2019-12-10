/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyKeywordArgument

class PyTypedDictCompletionContributor : CompletionContributor() {

  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inside(PyKeywordArgument::class.java), TotalityValueProvider)
  }

  private object TotalityValueProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val keywordArgument = PsiTreeUtil.getParentOfType(parameters.position, PyKeywordArgument::class.java)
      if (keywordArgument != null && keywordArgument.keyword == "total") {
        result.addElement(LookupElementBuilder.create("True").bold())
        result.addElement(LookupElementBuilder.create("False").bold())
      }
    }
  }
}
