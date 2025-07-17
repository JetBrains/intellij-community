// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures.junit5

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * @see CodeInsightTestFixtureImpl.doHighlighting
 * @see CodeInsightTestFixtureImpl.instantiateAndRun
 */
@TestOnly
fun Editor.doHighlighting(
  minimalSeverity: HighlightSeverity? = null,
  canChangeDocument: Boolean = false,
  readEditorMarkupModel: Boolean = false,
): List<HighlightInfo> = runBlocking {
  val highlightInfos = withContext(Dispatchers.EDT) {
    requireNotNull(project) { "PsiDocumentManager requires project to be not null" }
    val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)
    CodeInsightTestFixtureImpl.instantiateAndRun(
      psiFile!!, this@doHighlighting, ArrayUtilRt.EMPTY_INT_ARRAY, canChangeDocument, readEditorMarkupModel
    )
  }

  if (minimalSeverity == null) highlightInfos
  else {
    highlightInfos.filter { it.severity >= minimalSeverity }
  }
}