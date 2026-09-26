// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.typing.PyTypedDictTypeProvider.Helper.isTypingTypedDictInheritor
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_CLOSED_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_EXTRA_ITEMS_PARAMETER
import com.jetbrains.python.psi.types.PyTypedDictType.Companion.TYPED_DICT_TOTAL_PARAMETER
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Completes the `total=`, `closed=`, and `extra_items=` (PEP 728) class parameters inside the base-class list of a
 * `TypedDict` definition, e.g. `class Movie(TypedDict, <caret>)`.
 */
class PyTypedDictKeywordArgumentCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .withParents(PyReferenceExpression::class.java, PyArgumentList::class.java, PyClass::class.java),
      object : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
          val pyClass = parameters.position.parentOfType<PyClass>() ?: return
          val typeEvalContext = TypeEvalContext.codeCompletion(pyClass.project, parameters.originalFile)
          if (!pyClass.isTypingTypedDictInheritor(typeEvalContext)) return

          val existingKeywords = pyClass.superClassExpressionList?.arguments.orEmpty()
            .filterIsInstance<PyKeywordArgument>()
            .mapNotNull { it.keyword }
            .toSet()

          for (parameter in listOf(TYPED_DICT_TOTAL_PARAMETER, TYPED_DICT_CLOSED_PARAMETER, TYPED_DICT_EXTRA_ITEMS_PARAMETER)) {
            if (parameter !in existingKeywords) {
              result.addElement(LookupElementBuilder.create("$parameter="))
            }
          }
        }
      }
    )
  }
}
