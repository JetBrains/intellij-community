// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.JUnit5.util

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.PythonFileType


fun CodeInsightTestFixture.doTestByFile(file: PsiFile) {
  this.configureFromExistingVirtualFile(file.virtualFile)
  this.doHighlighting()
  this.checkHighlighting(true, false, true)
}

fun CodeInsightTestFixture.doTestByText(text: String) {
  this.configureByText(PythonFileType.INSTANCE, text)
  this.doHighlighting()
  this.checkHighlighting(true, false, true)
}