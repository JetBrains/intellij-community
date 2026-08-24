// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.setupPy

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider
import com.jetbrains.python.inspections.dependencies.DependencyMap
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStringLiteralExpression

private const val SETUP_PY = "setup.py"

/** `setup.py` `install_requires` entries mapped to their string literals. PSI-only, no interpreter. */
internal class SetupPyDependenciesPsiProvider : DependenciesPsiProvider<PyFile>(
  PyFile::class.java,
  PythonLanguage.INSTANCE,
) {
  override fun provideDependencies(file: PyFile): DependencyMap? {
    if (file.name != SETUP_PY) return null

    val setupCall = PsiTreeUtil.findChildrenOfType(file, PyCallExpression::class.java)
                      .firstOrNull { it.callee?.name == "setup" } ?: return null
    val installRequires = setupCall.getKeywordArgument("install_requires") ?: return null

    val dependencies = mutableMapOf<PyRequirement, PsiElement>()
    for (literal in PsiTreeUtil.findChildrenOfType(installRequires, PyStringLiteralExpression::class.java)) {
      val requirement = PyRequirementParser.fromLine(literal.stringValue) ?: continue
      dependencies[requirement] = literal
    }
    return dependencies.ifEmpty { null }
  }

  override val emptyFileInspectionMessage: @InspectionMessage String? = null
}
