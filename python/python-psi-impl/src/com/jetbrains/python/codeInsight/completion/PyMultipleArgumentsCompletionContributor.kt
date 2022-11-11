package com.jetbrains.python.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.IconManager
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.extensions.inArgumentList
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCallableParameter

class PyMultipleArgumentsCompletionContributor: CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement().inArgumentList(), MyCompletionProvider)
  }

  private object MyCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val position = parameters.position
      val argumentExplicitIndex = getArgumentIndex(position) ?: return

      val call = PsiTreeUtil.getParentOfType(position, PyCallExpression::class.java) ?: return
      val typeEvalContext = parameters.getTypeEvalContext()
      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)
      val callableTypes = call.multiResolveCallee(resolveContext)
      if (callableTypes.isEmpty()) return

      val scopeOwner = ScopeUtil.getScopeOwner(position) ?: return
      val names = collectNames(scopeOwner, position)

      callableTypes.forEach { callableType ->
        val callableParameters = callableType.getParameters(typeEvalContext)
        val argumentIndex = callableType.implicitOffset + argumentExplicitIndex
        if (callableParameters == null ||
            argumentIndex >= callableParameters.size ||
            callableParameters.any { it.isKeywordContainer || it.isPositionalContainer }) {
              return@forEach
        }

        val unfilledParameters = ContainerUtil.subList(callableParameters, argumentIndex)
        val variables = collectVariablesToComplete(unfilledParameters, names)
        if (variables.size > 1) {
          result.addElement(createParametersLookupElement(variables, call))
        }
      }
    }
  }

  companion object {
    val MULTIPLE_ARGUMENTS_VARIANT_KEY: Key<Boolean> = Key.create("py.multiple.arguments.completion.variant")

    private fun getArgumentIndex(position: PsiElement): Int? {
      val argumentList = PsiTreeUtil.getParentOfType(position, PyArgumentList::class.java) ?: return null
      if (argumentList.arguments.isEmpty()) return null
      if (argumentList.arguments.any { it is PyKeywordArgument || it is PyStarArgument }) return null
      if (!PsiTreeUtil.isAncestor(argumentList.arguments.last(), position, false)) return null
      return argumentList.arguments.size - 1
    }

    private fun createParametersLookupElement(variables: List<String>, call: PyCallExpression): LookupElement {
      return LookupElementBuilder.create(variables.joinToString(", "))
        .withIcon(IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Variable))
        .withInsertHandler(PyMultipleArgumentsInsertHandler(call))
        .apply {
          putUserData(MULTIPLE_ARGUMENTS_VARIANT_KEY, true)
        }
    }

    private fun collectVariablesToComplete(parameters: List<PyCallableParameter>, argumentsNames: Set<String>): List<String> {
      val variables = mutableListOf<String>()
      var keywordsOnlyFlag = false

      for (parameter in parameters) {
        if (parameter.parameter is PySlashParameter) continue
        if (parameter.parameter is PySingleStarParameter) {
          keywordsOnlyFlag = true
          continue
        }

        val paramName = parameter.name ?: return emptyList()
        if (paramName in argumentsNames) {
          if (!keywordsOnlyFlag) {
            variables.add(paramName)
          }
          else {
            variables.add("$paramName=$paramName")
          }
        }
        else {
          if (!parameter.hasDefaultValue()) return emptyList()
        }
      }

      return variables
    }

    private fun collectNames(scope: ScopeOwner, position: PsiElement): Set<String> =
      ControlFlowCache.getScope(scope).namedElements
        .asSequence()
        .filter { element ->
          PsiTreeUtil.getParentOfType(element, PyListCompExpression::class.java) ?.let { listComp ->
            PsiTreeUtil.isAncestor(listComp.resultExpression, position, false)
          } ?: PyPsiUtils.isBefore(element, position)
        }
        .mapNotNull { it.name }
        .toSet()
  }
}

class PyMultipleArgumentsInsertHandler(private val call: PyCallExpression): ParenthesesInsertHandler<LookupElement>() {
  override fun placeCaretInsideParentheses(context: InsertionContext?, item: LookupElement?): Boolean = false

  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val editor = context.editor
    context.commitDocument()
    if (call.argumentList?.closingParen == null) {
      editor.document.insertString(context.tailOffset, ")")
      editor.caretModel.moveToOffset(context.tailOffset)
    }
    else {
      editor.caretModel.moveToOffset(context.tailOffset + 1)
    }
  }
}