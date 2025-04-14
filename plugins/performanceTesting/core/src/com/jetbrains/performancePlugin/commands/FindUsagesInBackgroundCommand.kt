// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.find.FindManager
import com.intellij.find.actions.findUsages
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.find.usages.impl.searchTargets
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewElementsListener
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.FindUsagesCommand.Companion.getElement
import com.jetbrains.performancePlugin.commands.FindUsagesCommand.Companion.goToElement
import com.sampullara.cli.Args
import io.opentelemetry.context.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Command to execute finds usages in the background (results appear in the bottom menu and not in the popup)
 */
class FindUsagesInBackgroundCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: @NonNls String = "findUsagesInBackground"
    const val PREFIX: @NonNls String = CMD_PREFIX + NAME
    const val SPAN_NAME: @NonNls String = NAME
    const val FIRST_USAGE_SPAN_BACKGROUND: String = "${SPAN_NAME}_firstUsage"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val options = FindUsagesArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split(" ", limit = 2) }.toTypedArray(), false)

    val project = context.project
    val elementName = options.expectedName
    goToElement(options.position, elementName, context)

    val currentOTContext = Context.current()
    withContext(Dispatchers.EDT) {
      currentOTContext.makeCurrent().use {
        val editor = project.serviceAsync<FileEditorManager>().selectedTextEditor
        if (editor == null) {
          throw Exception("No editor is opened")
        }

        val (offset, scope) = writeIntentReadAction {
          Pair(editor.caretModel.offset, FindUsagesOptions.findScopeByName(project, null, options.scope))
        }

        val searchTargets = readAction {
          PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.let { searchTargets(it, offset) }
        }

        val element = getElement(project, editor, offset)

        val firstUsageSpan = PerformanceTestSpan
          .getTracer(isWarmupMode)
          .spanBuilder(FIRST_USAGE_SPAN_BACKGROUND)
          .setParent(PerformanceTestSpan.getContext())
          .startSpan()

        UsageViewElementsListener.EP_NAME.point.registerExtension(object : UsageViewElementsListener {
          override fun beforeUsageAdded(view: UsageView, usage: Usage) {
            // Unregister extension to avoid extra calls
            UsageViewElementsListener.EP_NAME.point.unregisterExtension(this)
            firstUsageSpan.end()
            super.beforeUsageAdded(view, usage)
          }
        })


        if (!searchTargets.isNullOrEmpty()) {
          val target = searchTargets.first()
          findUsages(false, project, scope, target)
        }
        else if (element != null) {
          if (!elementName.isNullOrEmpty()) {
            val foundElementName = readAction { (element as PsiNamedElement).name }
            check(foundElementName != null && foundElementName == elementName) { "Found element name $foundElementName does not correspond to expected $elementName" }
          }
          FindManager.getInstance(project).findUsages(element)
        }
      }
    }
  }

  override fun getName(): String = PREFIX
}