// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion.correctness.checker

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ml.impl.correctness.checker.CorrectnessCheckerBase
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl

class PythonCorrectnessChecker : CorrectnessCheckerBase(listOf(
  PyUnresolvedReferencesSemanticChecker,
  PyCallingNonCallableSemanticChecker,
  PyArgumentListSemanticChecker,
  PyRedeclarationSemanticChecker,
  PyAssignmentToLibraryScopeSemanticChecker,
)) {
  override fun buildPsiForSemanticChecks(file: PsiFile, suggestion: String, offset: Int, prefix: String): PsiFile {
    return PyExpressionCodeFragmentImpl(
      file.project,
      FileUtil.getNameWithoutExtension(file.name) + ".py",
      file.text.let { it.take(offset - prefix.length) + suggestion + " " + it.drop(offset) },
      true
    ).apply { context = file }
  }
}
