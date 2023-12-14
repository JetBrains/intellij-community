// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.TargetElementUtilBase
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.references.PyImportReference
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch
import com.jetbrains.python.psi.search.PySuperMethodsSearch
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.pyi.PyiUtil
import com.jetbrains.python.refactoring.PyRefactoringUiService
import com.jetbrains.python.sdk.PythonSdkUtil

/**
 * @author Aleksei.Kniazev
 */
class PyInlineFunctionHandler : InlineActionHandler() {
  override fun isEnabledForLanguage(l: Language?) = l is PythonLanguage

  override fun canInlineElement(element: PsiElement?): Boolean {
    if (element is PyFunction) {
      val containingFile = if (element.containingFile is PyiFile) PyiUtil.getOriginalElement(element)?.containingFile else element.containingFile
      return containingFile is PyFile
    }
    return false
  }

  override fun inlineElement(project: Project?, editor: Editor?, element: PsiElement?) {
    invoke(project, editor, element)
  }

  fun invoke(project: Project?, editor: Editor?, element: PsiElement?, showDialog: Boolean = true,
             inlineThisOnly: Boolean = false) {
    if (project == null || editor == null || element !is PyFunction) return
    val functionScope = ControlFlowCache.getScope(element)
    val error = when {
      element.isAsync -> PyPsiBundle.message("refactoring.inline.function.async")
      element.isGenerator -> PyPsiBundle.message("refactoring.inline.function.generator")
      PyUtil.isInitOrNewMethod(element) -> PyPsiBundle.message("refactoring.inline.function.constructor")
      PyBuiltinCache.getInstance(element).isBuiltin(element) -> PyPsiBundle.message("refactoring.inline.function.builtin")
      isSpecialMethod(element) -> PyPsiBundle.message("refactoring.inline.function.special.method")
      isUnderSkeletonDir(element) -> PyPsiBundle.message("refactoring.inline.function.skeleton.only")
      hasDecorators(element) -> PyPsiBundle.message("refactoring.inline.function.decorator")
      hasReferencesToSelf(element) -> PyPsiBundle.message("refactoring.inline.function.self.referrent")
      hasStarArgs(element) -> PyPsiBundle.message("refactoring.inline.function.star")
      overridesMethod(element, project) -> PyPsiBundle.message("refactoring.inline.function.overrides.method")
      isOverridden(element) -> PyPsiBundle.message("refactoring.inline.function.is.overridden")
      functionScope.hasGlobals() -> PyPsiBundle.message("refactoring.inline.function.global")
      functionScope.hasNonLocals() -> PyPsiBundle.message("refactoring.inline.function.nonlocal")
      hasNestedFunction(element) -> PyPsiBundle.message("refactoring.inline.function.nested")
      hasNonExhaustiveIfs(element) -> PyPsiBundle.message("refactoring.inline.function.interrupts.flow")
      else -> null
    }
    if (error != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, error, PyPsiBundle.message("refactoring.inline.function.title"), REFACTORING_ID)
      return
    }
    if (showDialog && !ApplicationManager.getApplication().isUnitTestMode) {
      PyRefactoringUiService.getInstance().showPyInlineFunctionDialog(project, editor, element, findReference(editor))
    }
    else {
      val processor = PyInlineFunctionProcessor(project, editor, element, findReference(editor), inlineThisOnly, true)
      processor.setPreviewUsages(false)
      processor.run()
    }
  }

  private fun isSpecialMethod(function: PyFunction): Boolean {
    return function.containingClass != null && function.name != null && 
           PyNames.getBuiltinMethods(LanguageLevel.forElement(function)).contains(function.name)
  }

  private fun hasNestedFunction(function: PyFunction): Boolean = SyntaxTraverser.psiTraverser(function.statementList).traverse().any { it is PyFunction }

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
    return parentIfStatement != null && checkInterruptsControlFlow(parentIfStatement, cache)
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

  private fun overridesMethod(function: PyFunction, project: Project): Boolean {
    return function.containingClass != null
           && PySuperMethodsSearch.search(function, TypeEvalContext.codeAnalysis(project, function.containingFile)).any()
  }

  private fun isOverridden(function: PyFunction): Boolean {
    return function.containingClass != null && PyOverridingMethodsSearch.search(function, true).any()
  }

  private fun hasStarArgs(function: PyFunction): Boolean {
    return function.parameterList.parameters.asSequence()
      .filterIsInstance<PyNamedParameter>()
      .any { it.isPositionalContainer || it.isKeywordContainer }
  }

  private fun hasReferencesToSelf(function: PyFunction): Boolean = SyntaxTraverser.psiTraverser(function.statementList)
    .any { it is PyReferenceExpression && it.reference.isReferenceTo(function) }

  private fun isUnderSkeletonDir(function: PyFunction): Boolean {
    val containingFile = PyiUtil.getOriginalElementOrLeaveAsIs(function, PyElement::class.java).containingFile
    val sdk = PythonSdkUtil.findPythonSdk(containingFile) ?: return false
    val skeletonsDir = PythonSdkUtil.findSkeletonsDir(sdk) ?: return false
    return VfsUtil.isAncestor(skeletonsDir, containingFile.virtualFile, true)
  }

  companion object {
    @JvmStatic
    fun getInstance(): PyInlineFunctionHandler {
      return EP_NAME.findExtensionOrFail(PyInlineFunctionHandler::class.java)
    }

    @JvmStatic
    val REFACTORING_ID = "refactoring.inlineMethod"

    @JvmStatic
    @VisibleForTesting
    fun findReference(editor: Editor): PsiReference? {
      val reference = TargetElementUtilBase.findReferenceWithoutExpectedCaret(editor)
      return if (reference !is PyImportReference) reference else null
    }
  }
}