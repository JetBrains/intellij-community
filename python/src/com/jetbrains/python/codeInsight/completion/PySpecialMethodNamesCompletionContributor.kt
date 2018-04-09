// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.extensions.python.afterDefInFunction
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile

class PySpecialMethodNamesCompletionContributor : CompletionContributor() {
  override fun handleAutoCompletionPossibility(context: AutoCompletionContext) = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterDefInFunction(), MyCompletionProvider)
  }

  private object MyCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
      val typeEvalContext = parameters.getTypeEvalContext()

      val pyClass = parameters.getPyClass()
      if (pyClass != null) {
        PyNames.getBuiltinMethods(LanguageLevel.forElement(pyClass))
          ?.forEach {
            addMethodToResult(result, pyClass, typeEvalContext, it.key, it.value.signature) { it.withTypeText("predefined") }
          }
      }
      else {
        val file = parameters.getFile()
        if (file != null) {
          PyNames
            .getModuleBuiltinMethods(LanguageLevel.forElement(file))
            .forEach {
              addFunctionToResult(result, file as? PyFile, it.key, it.value.signature) {
                it.withTypeText("predefined")
              }
            }
        }
      }
    }
  }
}