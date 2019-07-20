// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile

/**
 * @author Aleksei.Kniazev
 */
class PyInlineFunctionHandler : InlineActionHandler() {
  override fun isEnabledForLanguage(l: Language?) = l is PythonLanguage

  override fun canInlineElement(element: PsiElement?) = element is PyFunction && element.containingFile !is PyiFile

  override fun inlineElement(project: Project?, editor: Editor?, element: PsiElement?) {
    if (project == null || editor == null || element !is PyFunction) return
    val functionScope = ControlFlowCache.getScope(element)
    val error = when {
      element.isAsync -> "refactoring.inline.function.async"
      element.isGenerator -> "refactoring.inline.function.generator"
      PyNames.INIT == element.name -> "refactoring.inline.function.constructor"
      PyBuiltinCache.getInstance(element).isBuiltin(element) -> "refactoring.inline.function.builtin"
      hasDecorators(element) -> "refactoring.inline.function.decorator"
      hasReferencesToSelf(element) -> "refactoring.inline.function.self.referrent"
      hasStarArgs(element) -> "refactoring.inline.function.star"
      isOverride(element, project) -> "refactoring.inline.function.overridden"
      functionScope.hasGlobals() -> "refactoring.inline.function.global"
      functionScope.hasNonLocals() -> "refactoring.inline.function.nonlocal"
      functionScope.hasNestedScopes() -> "refactoring.inline.function.nested"
      hasNonExhaustiveIfs(element) -> "refactoring.inline.function.interrupts.flow"
      else -> null
    }
    if (error != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message(error), PyBundle.message("refactoring.inline.function.title"), REFACTORING_ID)
      return
    }
    if (!ApplicationManager.getApplication().isUnitTestMode){
      PyInlineFunctionDialog(project, editor, element, TargetElementUtil.findReference(editor)).show()
    }
  }

  private fun hasNonExhaustiveIfs(function: PyFunction): Boolean {
    val returns = mutableListOf<PyReturnStatement>()

    function.accept(object : PyRecursiveElementVisitor() {
      override fun visitPyReturnStatement(node: PyReturnStatement) {
        returns.add(node)
      }
    })

    if (returns.isEmpty()) return false
    val cache = mutableSetOf<PyIfStatement>()
    return returns.asSequence()
      .map { PsiTreeUtil.getParentOfType(it, PyIfStatement::class.java) }
      .distinct()
      .filterNotNull()
      .any { checkInterruptsControlFlow(it, cache) }
  }

  private fun checkInterruptsControlFlow(ifStatement: PyIfStatement, cache: MutableSet<PyIfStatement>): Boolean {
    if (ifStatement in cache) return false
    cache.add(ifStatement)
    val elsePart = ifStatement.elsePart
    if (elsePart == null) return true

    if (checkLastStatement(ifStatement.ifPart.statementList, cache)) return true
    if (checkLastStatement(elsePart.statementList, cache)) return true
    ifStatement.elifParts.forEach { if (checkLastStatement(it.statementList, cache)) return true }

    val parentIfStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement::class.java)
    if (parentIfStatement != null && checkInterruptsControlFlow(parentIfStatement, cache)) return true
    return false
  }

  private fun checkLastStatement(statementList: PyStatementList, cache: MutableSet<PyIfStatement>): Boolean {
    val statements = statementList.statements
    if (statements.isEmpty()) return true
    when(val last = statements.last()) {
      is PyIfStatement -> if (checkInterruptsControlFlow(last, cache)) return true
      !is PyReturnStatement -> return true
    }
    return false
  }

  private fun hasDecorators(function: PyFunction): Boolean = function.decoratorList?.decorators?.isNotEmpty() == true

  private fun isOverride(function: PyFunction, project: Project): Boolean {
    return function.containingClass != null
           && PySuperMethodsSearch.search(function, TypeEvalContext.codeAnalysis(project, function.containingFile)).any()
  }

  private fun hasStarArgs(function: PyFunction): Boolean {
    return function.parameterList.parameters.asSequence()
      .filterIsInstance<PyNamedParameter>()
      .any { it.isPositionalContainer || it.isKeywordContainer }
  }

  private fun hasReferencesToSelf(function: PyFunction): Boolean = SyntaxTraverser.psiTraverser(function.statementList)
    .any { it is PyReferenceExpression && it.reference.isReferenceTo(function) }

  companion object {
    @JvmStatic
    fun getInstance(): PyInlineFunctionHandler {
      return InlineActionHandler.EP_NAME.findExtensionOrFail(PyInlineFunctionHandler::class.java)
    }

    @JvmStatic
    val REFACTORING_ID = "refactoring.inlineMethod"
  }
}