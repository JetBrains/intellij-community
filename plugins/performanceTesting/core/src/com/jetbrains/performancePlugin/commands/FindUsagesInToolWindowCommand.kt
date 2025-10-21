// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.find.FindManager
import com.intellij.find.actions.findUsages
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.usages.impl.searchTargets
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.impl.UsageViewElementsListener
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.FindUsagesCommand.Companion.getElement
import com.jetbrains.performancePlugin.commands.FindUsagesCommand.Companion.goToElement
import com.sampullara.cli.Args
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Command to execute finds usages in the tool window (not in the popup), and to wait for the search to complete.
 */
class FindUsagesInToolWindowCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "findUsagesInToolWindow"
    const val PREFIX: String = CMD_PREFIX + NAME

    const val SPAN_NAME: String = NAME
    const val FIRST_USAGE_SPAN_NAME: String = "${NAME}_firstUsage"
    const val TOOL_WINDOW_SPAN_NAME: String = "${NAME}_toolWindow"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val options = FindUsagesArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split(" ", limit = 2) }.toTypedArray(), false)

    val project = context.project
    val elementName = options.expectedName
    goToElement(options.position, elementName, context)

    var span: Span? = null
    var firstUsageSpan: Span? = null
    var toolWindowSpan: Span? = null

    val currentOTContext = Context.current()
    withContext(Dispatchers.EDT) {
      currentOTContext.makeCurrent().use {
        val editor = project.serviceAsync<FileEditorManager>().selectedTextEditor
        if (editor == null) {
          throw Exception("No editor is opened")
        }

        val scope = readAction {
          FindUsagesOptions.findScopeByName(project, null, options.scope)
        }

        val rangeMarker = readAction {
          editor.document.createRangeMarker(editor.caretModel.offset, editor.caretModel.offset)
        }

        val searchTargets = readAction {
          PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.let { searchTargets(it, rangeMarker.startOffset) }
        }

        val element = getElement(project, editor, rangeMarker)

        if (!elementName.isNullOrEmpty()) {
          val foundElementName = readAction { (element as PsiNamedElement).name }
          check(foundElementName != null && foundElementName == elementName) { "Found element name $foundElementName does not correspond to expected $elementName" }
        }

        Registry.get("ide.find.result.count.warning.limit").setValue(Integer.MAX_VALUE)

        UsageViewElementsListener.EP_NAME.point.registerExtension(object : UsageViewElementsListener {
          override fun beforeUsageAdded(view: UsageView, usage: Usage) {
            UsageViewElementsListener.EP_NAME.point.unregisterExtension(this)

            firstUsageSpan?.end()
          }
        })

        val tracer = PerformanceTestSpan.getTracer(isWarmupMode)
        val parent = PerformanceTestSpan.getContext()

        span = tracer.spanBuilder(SPAN_NAME).setParent(parent).startSpan()
        firstUsageSpan = tracer.spanBuilder(FIRST_USAGE_SPAN_NAME).setParent(parent).startSpan()
        toolWindowSpan = tracer.spanBuilder(TOOL_WINDOW_SPAN_NAME).setParent(parent).startSpan()

        if (!searchTargets.isNullOrEmpty()) {
          findUsages(false, project, scope, searchTargets.first())
        }
        else {
          FindManager.getInstance(project).findUsages(element!!)
        }
      }
    }

    var usageView: UsageView? = null

    try {
      withTimeout(10.seconds) {
        usageView = UsageViewManager.getInstance(project).selectedUsageView
        while (usageView == null) {
          delay(50.milliseconds)
          usageView = UsageViewManager.getInstance(project).selectedUsageView
        }
      }
    }
    catch (_: TimeoutCancellationException) {
      throw Exception("Timeout while waiting for the usage view to open")
    }

    toolWindowSpan!!.end()

    while (usageView!!.isSearchInProgress) {
      delay(50.milliseconds)
    }

    span!!.setAttribute("number", usageView.usages.size.toLong())
    span.end()
  }

  override fun getName(): String = PREFIX
}
