// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.pyi.PyiUtil

/**
 * @author Aleksei.Kniazev
 */
class PyInlineFunctionDialog(project: Project,
                             private val myEditor: Editor,
                             private val myFunction: PyFunction,
                             private val myReference: PsiReference?) : InlineOptionsDialog(project, true, myFunction) {
  private val isMethod = myFunction.asMethod() != null
  private val myFunctionName = myFunction.name
  private val myNumberOfOccurrences: Int = getNumberOfOccurrences(myFunction)

  init {
    myInvokedOnReference = if (myReference != null) {
      val expression = myReference.element as PyReferenceExpression
      PsiTreeUtil.getParentOfType(expression, PyImportStatementBase::class.java) == null
    } else false
    title = if (isMethod) "Inline method $myFunctionName" else "Inline function $myFunctionName"
    init()
  }

  override fun doAction() {
    val originalFunction = PyiUtil.getOriginalElement(myFunction) as PyFunction?
    invokeRefactoring(PyInlineFunctionProcessor(myProject, myEditor, originalFunction ?: myFunction, myReference, isInlineThisOnly, !isKeepTheDeclaration))
  }

  override fun getNameLabelText(): String {
    val text = if (isMethod) "Method ${myFunctionName}" else "Function $myFunctionName"
    if (myNumberOfOccurrences != -1) {
      return "$text has $myNumberOfOccurrences occurrence${if (myNumberOfOccurrences == 1) "" else "s"}"
    }
    return text
  }
  override fun getBorderTitle(): String  = "Inline"
  override fun getInlineAllText(): String = PyBundle.message("refactoring.inline.all.remove.declaration")
  override fun getKeepTheDeclarationText(): String = PyBundle.message("refactoring.inline.all.keep.declaration")
  override fun getInlineThisText(): String = PyBundle.message("refactoring.inline.this.only")
  override fun getHelpId(): String = PyInlineFunctionHandler.REFACTORING_ID

  override fun allowInlineAll(): Boolean = true
  override fun isInlineThis(): Boolean = true

  override fun ignoreOccurrence(reference: PsiReference): Boolean {
    return PsiTreeUtil.getParentOfType(reference.element, PyImportStatementBase::class.java) == null
  }

  override fun getNumberOfOccurrences(nameIdentifierOwner: PsiNameIdentifierOwner?): Int {
    // TODO: this override will be redundant after PY-26881 is fixed and should be deleted then
    val originalNum = super.getNumberOfOccurrences(nameIdentifierOwner)
    val stubOrImplementation = if (PyiUtil.isInsideStub(myFunction)) PyiUtil.getOriginalElement(myFunction) else PyiUtil.getPythonStub(myFunction)
    if (originalNum != -1 && stubOrImplementation != null) {
      val fromDeclaration = ReferencesSearch.search(stubOrImplementation, GlobalSearchScope.projectScope(myProject)).asSequence()
        .filter(this::ignoreOccurrence)
        .count()
      return originalNum + fromDeclaration
    }
    return originalNum
  }
}