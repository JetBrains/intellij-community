// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.impl.PyFunctionBuilder
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil

class PyAddDunderMethodQuickFix(pyClass: PyClass, val dunderMethod: String) : PsiUpdateModCommandQuickFix() {
  private val myClassPointer = SmartPointerManager.createPointer(pyClass)
  private val myClassName = pyClass.name ?: "?"

  override fun getName(): String = PyPsiBundle.message("QFIX.add.dunder.method.to.class", dunderMethod, myClassName)
  override fun getFamilyName(): String = PyPsiBundle.message("QFIX.NAME.add.dunder.method.to.class", dunderMethod)

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val cls = updater.getWritable(myClassPointer.element) ?: return
    val stmtList = cls.statementList

    val method = PyFunctionBuilder(dunderMethod, cls).apply {
      parameter("self")
      if (dunderMethod == PyNames.DUNDER_FORMAT) {
        parameter("format_spec", PyNames.TYPE_STR)
      }
      statement("raise NotImplementedError('$myClassName.$dunderMethod not implemented')")
    }.buildFunction()

    PyPsiRefactoringUtil.addElementToStatementList(method, stmtList, false)
  }
}
