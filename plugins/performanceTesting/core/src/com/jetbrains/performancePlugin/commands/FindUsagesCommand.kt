// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.usages.impl.searchTargets
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.usages.Usage
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.sampullara.cli.Args
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Command to execute find usages with popup
 * Example: %findUsages [-position <elementName>] [-expectedName <elementName>] [-scope <All Places>] <WARMUP>
 */
class FindUsagesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "findUsages"
    const val PREFIX: @NonNls String = CMD_PREFIX + NAME
    const val SPAN_NAME: @NonNls String = NAME
    const val PARENT_SPAN_NAME: @NonNls String = SPAN_NAME + "Parent"
    private val LOG = logger<FindUsagesCommand>()

    @Suppress("TestOnlyProblems")
    suspend fun getElement(project: Project, editor: Editor, rangeMarker: RangeMarker): PsiElement? {
      return smartReadAction(project) {
        val offset = rangeMarker.startOffset
        if (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset) == null) {
          GotoDeclarationAction.findTargetElement(project, editor, offset)
        }
        else {
          GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
        }
      }
    }

    fun goToElement(position: String?, elementName: String?, context: PlaybackContext) {
      if (position != null) {
        val result = GoToNamedElementCommand(GoToNamedElementCommand.PREFIX + " $position $elementName", -1).execute(context)
        result.exceptionally { e ->
          throw Exception("fail to go to element $elementName", e)
        }
        result.get(30, TimeUnit.SECONDS)
      }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val options = FindUsagesArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split(" ", limit = 2) }.toTypedArray(), false)

    val project = context.project
    val position = options.position
    val elementName = options.expectedName
    goToElement(position, elementName, context)

    val currentOTContext = Context.current()
    var findUsagesFuture: Future<Collection<Usage>>? = null
    val storedPageSize = AdvancedSettings.getInt("ide.usages.page.size")
    val spanBuilder = PerformanceTestSpan.getTracer(isWarmupMode).spanBuilder(PARENT_SPAN_NAME).setParent(PerformanceTestSpan.getContext())
    var spanRef: Span? = null
    var scopeRef: Scope? = null
    withContext(Dispatchers.EDT) {
      currentOTContext.makeCurrent().use {
        val editor = project.serviceAsync<FileEditorManager>().selectedTextEditor
        if (editor == null) {
          throw Exception("No editor is opened")
        }

        val scope = readAction {
          FindUsagesOptions.findScopeByName(project, null, options.scope)
        }

        AdvancedSettings.setInt("ide.usages.page.size", Int.MAX_VALUE) //by default, it's 100; we need to find all usages to compare
        val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)

        val rangeMarker = readAction {
          editor.document.createRangeMarker(editor.caretModel.offset, editor.caretModel.offset)
        }

        val element = getElement(project, editor, rangeMarker)

        if (!elementName.isNullOrEmpty()) {
          val foundElementName = readAction { (element as PsiNamedElement).name }
          check(foundElementName != null && foundElementName == elementName) { "Found element name $foundElementName does not correspond to expected $elementName" }
        }

        val searchTargets = readAction {
          PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.let { searchTargets(it, rangeMarker.startOffset) }
        }
        if (!searchTargets.isNullOrEmpty()) {
          val target = searchTargets.first()

          LOG.info("Command find usages is called on target $target")

          spanRef = spanBuilder.startSpan()
          scopeRef = spanRef!!.makeCurrent()

          findUsagesFuture = ShowUsagesAction.startFindUsagesWithResult(project, target, popupPosition, editor, scope)
        }
        else if (element != null) {
          LOG.info("Command find usages is called on element $element")

          spanRef = spanBuilder.startSpan()
          scopeRef = spanRef!!.makeCurrent()

          findUsagesFuture = writeIntentReadAction { ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, scope) }
        }

        if (findUsagesFuture == null) {
          throw Exception("Can't find an element or search target at $position.")
        }
      }
    }

    val results = findUsagesFuture!!.get()
    spanRef!!.end()
    scopeRef!!.close()
    AdvancedSettings.setInt("ide.usages.page.size", storedPageSize)
    FindUsagesDumper.storeMetricsDumpFoundUsages(results.toMutableList(), project)
  }

  override fun getName(): String = PREFIX
}