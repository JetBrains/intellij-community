/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.AutoCompletionContext
import com.intellij.codeInsight.completion.AutoCompletionDecision
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.PyDataclassNames
import com.jetbrains.python.codeInsight.PyDataclassNames.Attrs
import com.jetbrains.python.codeInsight.PyDataclassNames.Dataclasses
import com.jetbrains.python.codeInsight.PyDataclassParameters
import com.jetbrains.python.codeInsight.hasPydanticDataclassDecorator
import com.jetbrains.python.codeInsight.isPydanticModel
import com.jetbrains.python.codeInsight.parseDataclassParameters
import com.jetbrains.python.codeInsight.stdlib.PyDataclassTypeProvider
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.extensions.afterDefInMethod
import com.jetbrains.python.extensions.inParameterList
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.PyParameterList
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PySubscriptionExpression

class PyDataclassCompletionContributor : CompletionContributor(), DumbAware {

  override fun handleAutoCompletionPossibility(context: AutoCompletionContext): AutoCompletionDecision = autoInsertSingleItem(context)

  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().afterDefInMethod(), PostInitProvider)
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inParameterList(), AttrsValidatorParameterProvider)
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiElement()
        .withParents(PyReferenceExpression::class.java, PyArgumentList::class.java, PyClass::class.java),
      PydanticModelKeywordProvider,
    )
  }

  private object PostInitProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val cls = parameters.getPyClass() ?: return
      val typeEvalContext = parameters.getTypeEvalContext()

      val dataclassParameters = parseDataclassParameters(cls, typeEvalContext)
      if (dataclassParameters == null || !dataclassParameters.init) return

      if (dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.STD) {
        val postInitParameters = mutableListOf(PyNames.CANONICAL_SELF)

        PyDataclassTypeProvider.Helper.getInitVars(cls, dataclassParameters, typeEvalContext).orEmpty().forEach {
          val name = it.targetExpression.name
          val typeHint = PyTypingTypeProvider.getAnnotationValue(it.targetExpression, typeEvalContext)
          if (name != null && typeHint is PySubscriptionExpression) {
            val indexExpression = typeHint.indexExpression
            val parameterString = if (indexExpression != null) {
              "${name}: ${indexExpression.text}"
            }
            else {
              name
            }
            postInitParameters.add(parameterString)
          }
        }

        addMethodToResult(result, cls, typeEvalContext,
                          Dataclasses.DUNDER_POST_INIT, postInitParameters.joinToString(prefix = "(", postfix = ")"))
      }
      else if (dataclassParameters.type.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
        addMethodToResult(result, cls, typeEvalContext, Attrs.DUNDER_POST_INIT)
      }
    }
  }

  private object AttrsValidatorParameterProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val cls = parameters.getPyClass() ?: return

      val parameterList = PsiTreeUtil.getParentOfType(parameters.position, PyParameterList::class.java) ?: return
      val parameter = PsiTreeUtil.getParentOfType(parameters.position, PyParameter::class.java) ?: return

      val index = parameterList.parameters.indexOf(parameter)
      if (index != 1 && index != 2) return

      val decorators = parameterList.containingFunction?.decoratorList ?: return
      if (decorators.decorators.none { it.qualifiedName?.endsWith("validator") == true }) return

      val typeEvalContext = parameters.getTypeEvalContext()

      if (parseDataclassParameters(cls, typeEvalContext)?.type?.asPredefinedType == PyDataclassParameters.PredefinedType.ATTRS) {
        result.addElement(LookupElementBuilder.create(if (index == 1) "attribute" else "value").withIcon(AllIcons.Nodes.Parameter))
      }
    }
  }

  private object PydanticModelKeywordProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val cls = PsiTreeUtil.getParentOfType(parameters.position, PyClass::class.java) ?: return
      val typeEvalContext = parameters.getTypeEvalContext()
      if (!isPydanticModel(cls, typeEvalContext) || hasPydanticDataclassDecorator(cls, typeEvalContext)) return

      val existingKeywords = cls.superClassExpressionList?.arguments.orEmpty()
        .filterIsInstance<PyKeywordArgument>()
        .mapNotNull { it.keyword }
        .toSet()

      for (keyword in getPydanticConfigDictKeys(cls)) {
        if (keyword !in existingKeywords) {
          result.addElement(
            LookupElementBuilder.create("$keyword=")
              .withIcon(AllIcons.Nodes.Parameter)
          )
        }
      }
    }

    private fun getPydanticConfigDictKeys(anchor: PyClass): Set<String> {
      val psiFacade = PyPsiFacade.getInstance(anchor.project)
      val configDict = PyDataclassNames.Pydantic.CONFIG_DICT_QUALIFIED_NAMES
                         .firstNotNullOfOrNull { psiFacade.createClassByQName(it, anchor) }
                       ?: return emptySet()
      return configDict.classAttributes.mapNotNull { it.name }.toSet()
    }
  }
}
