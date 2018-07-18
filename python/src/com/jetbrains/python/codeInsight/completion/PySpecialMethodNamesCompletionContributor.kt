// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.extensions.python.afterDefInFunction
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyNames.PREPARE
import com.jetbrains.python.inspections.PyMethodParametersInspection
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PySpecialMethodNamesCompletionContributor : CompletionContributor() {
  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision = autoInsertSingleItem(context)

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
            val name = it.key
            val signature = it.value.signature

            if (name == PREPARE) {
              handlePrepare(result, pyClass, typeEvalContext, signature)
            }
            else {
              addMethodToResult(result, pyClass, typeEvalContext, name, signature) { it.withTypeText("predefined") }
            }
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

    private fun handlePrepare(result: CompletionResultSet, pyClass: PyClass, context: TypeEvalContext, signature: String) {
      val mcs = PyMethodParametersInspection.getInstance(pyClass)?.MCS
      val signatureAfterApplyingSettings = if (mcs == null) signature else signature.replace("metacls", mcs)

      addMethodToResult(result, pyClass, context, PREPARE, signatureAfterApplyingSettings) {
        it.withTypeText("predefined")
        it.withInsertHandler { context, _ ->
          val function = PsiTreeUtil.getParentOfType(context.file.findElementAt(context.startOffset), PyFunction::class.java)

          if (function != null && function.modifier != PyFunction.Modifier.CLASSMETHOD) {
            PyUtil.addDecorator(function, "@${PyNames.CLASSMETHOD}")
          }
        }
      }
    }
  }
}