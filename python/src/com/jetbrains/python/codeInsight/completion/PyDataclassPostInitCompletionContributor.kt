/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.extensions.python.afterDefInMethod
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.DUNDER_POST_INIT
import com.jetbrains.python.codeInsight.stdlib.DATACLASSES_INITVAR_TYPE
import com.jetbrains.python.codeInsight.stdlib.parseDataclassParameters
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyClassType

class PyDataclassPostInitCompletionContributor : CompletionContributor() {

  override fun handleAutoCompletionPossibility(context: AutoCompletionContext) = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterDefInMethod(), MyCompletionProvider)
  }

  private object MyCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
      val cls = parameters.getPyClass() ?: return
      val typeEvalContext = parameters.getTypeEvalContext()

      if (parseDataclassParameters(cls, typeEvalContext)?.init == true) {
        val postInitParameters = mutableListOf(PyNames.CANONICAL_SELF)

        cls.processClassLevelDeclarations { element, _ ->
          if (element is PyTargetExpression && element.annotationValue != null) {
            val name = element.name
            val annotationValue = element.annotation?.value as? PySubscriptionExpression

            if (name != null && annotationValue != null) {
              val type = typeEvalContext.getType(element)

              if (type is PyClassType && type.classQName == DATACLASSES_INITVAR_TYPE) {
                val typeHint = annotationValue.indexExpression.let { if (it == null) "" else ": ${it.text}" }
                postInitParameters.add(name + typeHint)
              }
            }
          }

          true
        }

        addMethodToResult(result, cls, typeEvalContext, DUNDER_POST_INIT, postInitParameters.joinToString(prefix = "(", postfix = ")"))
      }
    }
  }
}