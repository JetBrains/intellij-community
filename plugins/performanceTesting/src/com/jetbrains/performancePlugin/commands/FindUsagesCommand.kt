package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiNamedElement
import com.intellij.usages.Usage
import com.sampullara.cli.Args
import io.opentelemetry.context.Context
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
    const val PREFIX: @NonNls String = CMD_PREFIX + "findUsages"
    const val SPAN_NAME: @NonNls String = "findUsages"
    private val LOG = logger<FindUsagesCommand>()
  }

  @Suppress("TestOnlyProblems")
  override suspend fun doExecute(context: PlaybackContext) {
    val options = FindUsagesArguments()
    Args.parse(options, extractCommandArgument(PREFIX).split("|").flatMap { it.split(" ", limit= 2) }.toTypedArray())

    val position = options.position
    val elementName = options.expectedName
    if (position != null) {
      val result = GoToNamedElementCommand(GoToNamedElementCommand.PREFIX + " $position $elementName", -1).execute(context)
      result.exceptionally { e ->
        throw Exception("fail to go to element $elementName", e)
      }
      result.get(30, TimeUnit.SECONDS)
    }

    val currentOTContext = Context.current()
    var findUsagesFuture: Future<Collection<Usage>>
    val storedPageSize = AdvancedSettings.getInt("ide.usages.page.size")
    withContext(Dispatchers.EDT) {
      currentOTContext.makeCurrent().use {
        val editor = FileEditorManager.getInstance(context.project).selectedTextEditor
        if (editor == null) {
          throw Exception("No editor is opened")
        }
        val offset = editor.caretModel.offset
        val element = smartReadAction(context.project) {
          if (GotoDeclarationAction.findElementToShowUsagesOf(editor, offset) == null) {
            GotoDeclarationAction.findTargetElement(context.project, editor, offset)
          }
          else {
            GotoDeclarationAction.findElementToShowUsagesOf(editor, offset)
          }
        }
        if (element == null) {
          throw Exception("Can't find an element under $offset offset.")
        }

        val foundElementName = (element as PsiNamedElement).name
        if (!elementName.isNullOrEmpty()) {
          check(
            foundElementName != null && foundElementName == elementName) { "Found element name $foundElementName does not correspond to expected $elementName" }
        }
        LOG.info("Command find usages is called on element $element")

        val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)

        //configuration for find usages
        AdvancedSettings.setInt("ide.usages.page.size", Int.MAX_VALUE) //by default, it's 100; we need to find all usages to compare
        val scope = FindUsagesOptions.findScopeByName(context.project, null, options.scope)
        findUsagesFuture = ShowUsagesAction.startFindUsagesWithResult(element, popupPosition, editor, scope)
      }

    }
    val results = findUsagesFuture.get()
    AdvancedSettings.setInt("ide.usages.page.size", storedPageSize)
    FindUsagesDumper.storeMetricsDumpFoundUsages(results.toMutableList(), context.project)
  }

  override fun getName(): String = PREFIX
}