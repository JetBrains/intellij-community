package com.intellij.jupyter.ui.test.util.codeinsight

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.HighlightInfo
import com.intellij.driver.sdk.InspectionProfileImpl
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.waitForCodeAnalysis

fun Driver.codeAnalysisResults(editor: Editor): Collection<HighlightInfo> = run {
  waitForCodeAnalysis(singleProject(), editor.getVirtualFile())
  getHighlights(editor.getDocument())
}

/**
 * Returns [HighlightInfo] which match to a specific [InspectionProfileImpl]'s short name.
 * Be aware that 'unused' profile declarations might also contain special notebook error highlighters.
 * To distinguish, true unused inspections are considered as warnings.
 */
fun Driver.codeAnalysisResultsByInspectionProfile(editorComponent: NotebookEditorUiComponent, profileShortName: String): Collection<HighlightInfo> {
  val results = codeAnalysisResults(editorComponent.editor)
  val file = withReadAction {
    editorComponent.psiFile!!
  }
  return filterInfosByInspectionProfile(file, results, profileShortName)
}
