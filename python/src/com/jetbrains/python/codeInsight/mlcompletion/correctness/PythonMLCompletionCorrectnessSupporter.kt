// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion.correctness

import com.intellij.platform.ml.impl.correctness.MLCompletionCorrectnessSupporterBase
import com.jetbrains.python.codeInsight.mlcompletion.correctness.autoimport.PythonImportFixer
import com.jetbrains.python.codeInsight.mlcompletion.correctness.checker.PythonCorrectnessChecker

class PythonMLCompletionCorrectnessSupporter : MLCompletionCorrectnessSupporterBase() {
  override val correctnessChecker: PythonCorrectnessChecker = PythonCorrectnessChecker()

  override val importFixer: PythonImportFixer = PythonImportFixer()
}