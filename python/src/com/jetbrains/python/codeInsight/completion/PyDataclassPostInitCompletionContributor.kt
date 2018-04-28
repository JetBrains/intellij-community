/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.extensions.python.afterDefInMethod
import com.jetbrains.extensions.python.inParameterList
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.stdlib.*
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyClassType

class PyDataclassPostInitCompletionContributor : CompletionContributor() {

  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterDefInMethod(), PostInitProvider)
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inParameterList(), AttrsValidatorParameterProvider)
  }

  private object PostInitProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
      val cls = parameters.getPyClass() ?: return
      val typeEvalContext = parameters.getTypeEvalContext()

      val dataclassParameters = parseDataclassParameters(cls, typeEvalContext)
      if (dataclassParameters == null || !dataclassParameters.init) return

      if (dataclassParameters.type == PyDataclassParameters.Type.STD) {
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
      else if (dataclassParameters.type == PyDataclassParameters.Type.ATTRS) {
        addMethodToResult(result, cls, typeEvalContext, DUNDER_ATTRS_POST_INIT)
      }
    }
  }

  private object AttrsValidatorParameterProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
      val cls = parameters.getPyClass() ?: return

      val parameterList = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java) ?: return
      val parameter = PsiTreeUtil.getParentOfType(parameters.position, PyParameter::class.java) ?: return

      val index = parameterList.parameters.indexOf(parameter)
      if (index != 1 && index != 2) return

      val decorators = parameterList.containingFunction?.decoratorList ?: return
      if (decorators.decorators.none { it.qualifiedName?.endsWith("validator") == true }) return

      val typeEvalContext = parameters.getTypeEvalContext()

      if (parseDataclassParameters(cls, typeEvalContext)?.type == PyDataclassParameters.Type.ATTRS) {
        result.addElement(LookupElementBuilder.create(if (index == 1) "attribute" else "value").withIcon(AllIcons.Nodes.Parameter))
      }
    }
  }
}