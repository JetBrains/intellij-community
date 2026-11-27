// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.intellij.codeInsight.hint.EditorHintListener
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

/**
 * Searches for the given name in the shown Search Everywhere popup, and checks that the results have an item of that name.
 * The Symbols tab shows the workspace symbols of the server among its results. Open the popup on that tab first,
 * with `%searchEverywhere -tab symbol`: the first popup of a session takes longer to open than the search does.
 * The command puts the name into the search field at once, as a paste does, and ends when the search of all
 * contributors of the tab has finished. The popup stays open.
 *
 * Syntax: `%lspSearchEverywhere <the name of the item>`
 */
internal class LspSearchEverywhereCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspSearchEverywhere"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val name = extractCommandArgument(PREFIX)
    require(name.isNotBlank()) { "Expected: $PREFIX <the name of the item>, got: '$text'" }
    val search = withContext(Dispatchers.EDT) {
      val popup = SearchEverywhereManager.getInstance(context.project).currentlyShownPopupInstance
                  ?: error("No Search Everywhere popup is shown")
      // the only API that gives the results of a search, which `%searchEverywhere` uses too
      @Suppress("TestOnlyProblems")
      val future = popup.findElementsForPattern(name)
      future
    }
    val items = try {
      withContext(Dispatchers.IO) { search.get(DEFAULT_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS) }
    }
    catch (e: TimeoutException) {
      throw IllegalStateException("Search Everywhere did not finish the search of '$name' in $DEFAULT_TIMEOUT", e)
    }
    val names = items.map { it.itemName() }
    check(name in names) { "Search Everywhere found no '$name', it found: $names" }
  }
}

/**
 * Runs Parameter Info at the caret of the selected editor, as its shortcut does, and waits until its hint highlights
 * a parameter with the given text. For the hint, the LSP client asks the server for the signature help twice:
 * for the signatures that the hint shows, and then for the active parameter, which the hint highlights.
 * The hint has no model that a listener could watch for the highlight, so the command checks the hint every 10 ms.
 * It closes the hint at the end, so that the hint makes no requests during the next commands.
 *
 * Syntax: `%lspParameterInfo <the text of the highlighted parameter>`
 */
internal class LspParameterInfoCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspParameterInfo"
    private val CHECK_INTERVAL = 10.milliseconds

    /** The text of the hint writes the highlighted parameter in bold, see `ParameterInfoComponent.toString`. */
    private val HIGHLIGHTED_PARAMETER = Regex("<b(?: [^>]*)?>(.*?)</b>")
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val parameterText = extractCommandArgument(PREFIX)
    require(parameterText.isNotBlank()) { "Expected: $PREFIX <the text of the highlighted parameter>, got: '$text'" }
    val project = context.project
    val editor = selectedEditor(project)
    val shownHints = CopyOnWriteArrayList<LightweightHint>()
    val connection = ApplicationManager.getApplication().messageBus.connect()
    try {
      connection.subscribe(EditorHintListener.TOPIC, object : EditorHintListener {
        override fun hintShown(hintEditor: Editor, hint: LightweightHint, flags: Int, hintInfo: HintHint) {
          if (hintEditor == editor) shownHints.add(hint)
        }
      })
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
          val event = AnActionEvent.createEvent(action, EditorUtil.getEditorDataContext(editor), null,
                                                ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null)
          ActionUtil.updateAction(action, event)
          check(event.presentation.isEnabled) { "Parameter Info is disabled at the caret" }
          check(ActionUtil.performAction(action, event).isPerformed) { "Parameter Info did not run" }
        }
      }

      var hintTexts = emptyList<String>()
      val hint = withTimeoutOrNull(DEFAULT_TIMEOUT) {
        var found: LightweightHint? = null
        while (found == null) {
          val visibleHints = withContext(Dispatchers.EDT) { shownHints.filter { it.isVisible }.associateWith { it.component.toString() } }
          hintTexts = visibleHints.values.toList()
          found = visibleHints.entries.firstOrNull { hint ->
            HIGHLIGHTED_PARAMETER.findAll(hint.value).any { parameterText in it.groupValues[1] }
          }?.key
          if (found == null) delay(CHECK_INTERVAL)
        }
        found
      }
      checkNotNull(hint) { "Parameter Info did not highlight '$parameterText' in $DEFAULT_TIMEOUT. The hints of the editor: $hintTexts" }
      withContext(Dispatchers.EDT) { hint.hide() }
    }
    finally {
      connection.disconnect()
    }
  }
}

private fun Any.itemName(): String {
  // a goto contributor gives its items in a wrapper with their presentation, as the test fixture of Search Everywhere unwraps them
  @Suppress("DEPRECATION")
  val item = PSIPresentationBgRendererWrapper.getItem(this)
  return (item as? NavigationItem)?.name ?: item.toString()
}
