// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inlayHints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext


class PythonInlayParameterHintsProvider : InlayParameterHintsProvider {

  companion object {
    val showForClassConstructorCalls: Option = Option("python.show.class.constructor.call.parameter.names",
                                                      PyBundle.messagePointer(
                                                        "inlay.parameters.python.show.class.constructor.call.parameter.names"),
                                                      true)

    val showForNonLiteralArguments: Option = Option("python.show.hints.for.non-literal.arguments",
                                                    PyBundle.messagePointer(
                                                      "inlay.parameters.python.show.hints.for.non-literal.arguments"),
                                                    false)
  }

  private fun getInlayInfoForArgumentList(node: PyArgumentList): List<InlayInfo> {
    if (node.parent is PyClass || node.arguments.size == 1) {
      return emptyList()
    }

    val context = TypeEvalContext.codeAnalysis(node.project, node.containingFile)
    val resolveContext = PyResolveContext.defaultContext(context)

    val callExpression = node.callExpression ?: return emptyList()
    val argumentMappings = callExpression.multiMapArguments(resolveContext)
    val mapping = if (argumentMappings.isParameterHintSafeOverloads()) argumentMappings.first() else return emptyList()

    val callable = mapping.callableType?.callable

    if (callable == null || (PyUtil.isInitOrNewMethod(callable) && !showForClassConstructorCalls.isEnabled())) {
      return emptyList()
    }

    val info = mutableListOf<InlayInfo>()
    val mappedParameters = mapping.mappedParameters

    mappedParameters.forEach { (argument, parameter) ->
      if (parameter != null && argument != null) {
        if (parameter.isPositionalContainer) {
          info.add(InlayInfo("*${parameter.name}", argument.textOffset))
          return info
        }
        if (parameter.isKeywordContainer) {
          return info
        }
        if (argument !is PyKeywordArgument) {
          if (argument.isLiteralArgument() || showForNonLiteralArguments.isEnabled()) {
            info.add(InlayInfo("${parameter.name}", argument.textOffset))
          }
        }
      }
    }
    return info
  }

  /**
   * Determines whether it is safe to show hints for a method.
   *
   * @return {@code true} if the method does not have overloads or all the overloads have the same signature, i.e.,
   * all parameters have the same names and are placed in the same order.
   * Otherwise, return {@code false}.
   */
  private fun List<PyArgumentsMapping>.isParameterHintSafeOverloads(): Boolean {
    if (this.size == 1) return true

    return this
      .asSequence()
      .map { mapping -> mapping.mappedParameters }
      .map { parameters -> parameters.values }
      .map { listOfParameters ->
        listOfParameters.map { parameter ->
          parameter.getPresentableText(false)
        }
      }
      .distinct()
      .count() == 1
  }

  override fun getHintInfo(element: PsiElement): HintInfo? {
    if (element is PyArgumentList) {
      val parent = element.parent

      if (parent is PyCallExpression) {
        val callee = parent.callee

        if (callee == null) return null

        val context = TypeEvalContext.codeAnalysis(element.project, element.containingFile)
        val resolveContext = PyResolveContext.defaultContext(context)

        val callableType = parent.multiResolveCallee(resolveContext).firstOrNull() ?: return null
        val callable = callableType.callable

        if (callable is PyQualifiedNameOwner) {
          val qName = callable.qualifiedName
          val fullQName = if (PyBuiltinCache.isInBuiltins(callee)) qName?.toQNameForBuiltins() else qName
          val parameterList = callable.parameterList

          if (fullQName != null) {
            return HintInfo.MethodInfo(fullQName, parameterList.parameters
              .map { parameter ->
                parameter.asNamed?.getRepr(false)?.replace("*", "<star>")
                ?: if (parameter is PySlashParameter) "/" else "*"
              })
          }
        }
      }
    }
    return null
  }

  private fun PyExpression.isLiteralArgument() =
    this is PyLiteralExpression ||
    this is PyListLiteralExpression ||
    this is PyDictLiteralExpression ||
    (this is PyParenthesizedExpression && this.children.first() is PyTupleExpression)

  private fun String.toQNameForBuiltins(): String {
    val components = mutableListOf("builtins")
    components.addAll(this.split("."))
    return QualifiedName.fromComponents(components).toString()
  }

  override fun getParameterHints(element: PsiElement, file: PsiFile) =
    if (element is PyArgumentList) {
      getInlayInfoForArgumentList(element)
    }
    else emptyList()

  override fun getDefaultBlackList() =
    setOf("builtins.*",
          "typing.*")

  override fun getBlacklistExplanationHTML(): String {
    return PyBundle.message("inlay.parameters.python.hints.blacklist.explanation",
                            KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
  }

  override fun getSupportedOptions() =
    listOf(showForClassConstructorCalls,
           showForNonLiteralArguments)

  override fun isBlackListSupported(): Boolean = true
}