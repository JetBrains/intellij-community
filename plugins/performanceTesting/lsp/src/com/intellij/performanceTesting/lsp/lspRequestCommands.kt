// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.intellij.find.actions.FindUsagesAction
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Searches for the usages of the name at the caret with the search target that the editor gives to the Show Usages action.
 * The usual `%findUsages` command looks for a PSI element or for a symbol of the PSI file, and finds neither in a file
 * that the IDE supports through an LSP server only: the LSP client gives its search target to the Find Usages
 * and Show Usages actions through the data context of the editor.
 *
 * The span [SPAN_NAME] lasts from the start of the search until the popup has all usages.
 *
 * Syntax: `%lspFindUsages [the least number of usages to find]`
 */
internal class LspFindUsagesCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspFindUsages"
    const val SPAN_NAME: String = "lspFindUsages"
    private const val PAGE_SIZE_SETTING = "ide.usages.page.size"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val leastUsageCount = extractCommandArgument(PREFIX).toIntOrNull() ?: 1
    val project = context.project
    val editor = selectedEditor(project)
    val scope = readAction { FindUsagesOptions.findScopeByName(project, null, "Project Files") }

    val storedPageSize = AdvancedSettings.getInt(PAGE_SIZE_SETTING)
    // the popup stops the search when the first page is full
    AdvancedSettings.setInt(PAGE_SIZE_SETTING, Int.MAX_VALUE)
    try {
      val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext()).startSpan()
      val usages = try {
        val search = withContext(Dispatchers.EDT) {
          val targets = FindUsagesAction.SEARCH_TARGETS.getData(EditorUtil.getEditorDataContext(editor)).orEmpty()
          val target = targets.firstOrNull() ?: error("The editor has no search target at the caret")
          ShowUsagesAction.startFindUsagesWithResult(project, target, JBPopupFactory.getInstance().guessBestPopupLocation(editor), editor, scope)
        }
        withContext(Dispatchers.IO) { search.get() }
      }
      finally {
        span.end()
      }
      check(usages.size >= leastUsageCount) { "Found ${usages.size} usages, expected $leastUsageCount or more: $usages" }
    }
    finally {
      AdvancedSettings.setInt(PAGE_SIZE_SETTING, storedPageSize)
    }
  }
}

/**
 * Gets the documentation of the name at the caret, the one that Quick Documentation and a mouse hover show.
 * No other command measures the documentation. This one does not show the popup: the span is the time
 * in which the content gets ready, and the popup would add the time of the UI to it.
 *
 * The span [SPAN_NAME] lasts from the request for the documentation targets until the documentation is ready as HTML.
 *
 * Syntax: `%lspHover <text that the documentation has>`
 */
internal class LspHoverCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspHover"
    const val SPAN_NAME: String = "lspHover"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val expectedText = extractCommandArgument(PREFIX)
    val project = context.project
    val editor = selectedEditor(project)
    val offset = withContext(Dispatchers.EDT) { editor.caretModel.offset }

    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext()).startSpan()
    val html = try {
      val targets = readAction {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: error("The editor has no PSI file")
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, psiFile, offset)
      }
      targets.mapNotNull { target -> readAction { (target.computeDocumentation() as? DocumentationData)?.html } }
    }
    finally {
      span.end()
    }
    check(html.any { it.contains(expectedText) }) { "No documentation with '$expectedText' at the caret, got: $html" }
  }
}
