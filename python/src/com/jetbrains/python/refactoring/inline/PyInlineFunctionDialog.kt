// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.inline.InlineOptionsDialog
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.pyi.PyiUtil

/**
 * @author Aleksei.Kniazev
 */
public class PyInlineFunctionDialog(project: Project,
                             private val myEditor: Editor,
                             private val myFunction: PyFunction,
                             private val myInvocationReference: PsiReference?) : InlineOptionsDialog(project, true, myFunction) {
  private val isMethod = myFunction.asMethod() != null
  private val myFunctionName = myFunction.name
  private val myNumberOfOccurrences: Int = getNumberOfOccurrences(myFunction)

  init {
    myInvokedOnReference = myInvocationReference != null
    title = when {
      isMethod -> PyPsiBundle.message("refactoring.inline.method", myFunctionName)
      else -> PyPsiBundle.message("refactoring.inline.function", myFunctionName)
    }
    init()
  }

  override fun doAction() {
    val originalFunction = PyiUtil.getOriginalElement(myFunction) as PyFunction?
    invokeRefactoring(
      PyInlineFunctionProcessor(myProject, myEditor, originalFunction ?: myFunction, myInvocationReference, isInlineThisOnly, !isKeepTheDeclaration))
  }

  override fun getNameLabelText(): String {
    val text = if (isMethod) PyPsiBundle.message("refactoring.inline.label.method", myFunctionName) else PyPsiBundle.message("refactoring.inline.label.function", myFunctionName)
    if (myNumberOfOccurrences != -1) {
      return PyPsiBundle.message("refactoring.name.label.text", text, myNumberOfOccurrences, if (myNumberOfOccurrences == 1) 0 else 1)
    }
    return text
  }
  override fun getBorderTitle(): String  = PyPsiBundle.message("refactoring.inline.all.border.title")
  override fun getInlineAllText(): String = PyPsiBundle.message("refactoring.inline.all.remove.declaration")
  override fun getKeepTheDeclarationText(): String = PyPsiBundle.message("refactoring.inline.all.keep.declaration")
  override fun getInlineThisText(): String = PyPsiBundle.message("refactoring.inline.this.only")
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
      val fromOtherLocation = super.getNumberOfOccurrences(stubOrImplementation as PsiNameIdentifierOwner)
      if (fromOtherLocation != -1) return originalNum + fromOtherLocation
    }
    return originalNum
  }
}