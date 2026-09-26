// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.actions.ShowSettingsAction
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.NonModalSettingsPolicy
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.newEditor.SettingsDialogPerformanceTracker
import com.intellij.openapi.options.newEditor.SettingsNonModalDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.awt.Window
import java.awt.event.WindowEvent
import kotlin.time.Duration.Companion.minutes

/**
 * Opens the non-modal Settings window and records the spans produced by [SettingsDialogPerformanceTracker].
 *
 * Syntax: `%measureSettingsDialog <openDialog|openById|openByClass> phase <phase> [id <configurableId>] [class <configurableClass>]`
 *
 * Only the non-modal Settings window is instrumented, so a run must enable it with the `ide.ui.non.modal.settings.window`
 * system property. A modal window would block the EDT in its own event loop and the command would hang until the readiness timeout.
 *
 * The command may be used only once per IDE launch, and the Settings window must not have been opened before it,
 * because a reused window loads no page and the command would then fail on the readiness timeout. See
 * [SettingsDialogPerformanceTracker.start].
 */
class MeasureSettingsDialogCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = AbstractCommand.CMD_PREFIX + "measureSettingsDialog"

    const val MODE_OPEN_DIALOG: @NonNls String = "openDialog"
    const val MODE_OPEN_BY_ID: @NonNls String = "openById"
    const val MODE_OPEN_BY_CLASS: @NonNls String = "openByClass"

    const val PHASE_EARLY_PROJECT_OPEN: @NonNls String = "earlyProjectOpen"
    const val PHASE_AFTER_SMART_MODE: @NonNls String = "afterSmartMode"

    private val SETTINGS_READY_TIMEOUT = 2.minutes

    /**
     * A span name states both how Settings was opened ([mode]) and the state the IDE was in ([phase]), because the two are
     * independent and the numbers are not comparable across either of them. Tests must derive the names they publish from
     * this function, so that a scenario cannot report a name that disagrees with what it measured.
     */
    @JvmStatic
    fun spanNames(mode: String, phase: String): SpanNames {
      val suffix = when (mode) {
        MODE_OPEN_DIALOG -> phase
        MODE_OPEN_BY_ID -> "byId.$phase"
        MODE_OPEN_BY_CLASS -> "byClass.$phase"
        else -> error("Unsupported Settings dialog measurement mode: $mode")
      }
      return SpanNames(dialogShown = "settings.dialog.shown.$suffix",
                       pageReady = "settings.page.ready.$suffix",
                       configurableTreeBuilt = "settings.configurableTree.built.$suffix")
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val arguments = extractCommandArgument(PREFIX).split(' ').filter { it.isNotBlank() }
    val mode = arguments.getOrNull(0) ?: error("Missing Settings dialog measurement mode")
    val phase = arguments.parameter("phase") ?: PHASE_AFTER_SMART_MODE
    val spanNames = spanNames(mode, phase)

    // a modal window would block the EDT in its own event loop, so the command would hang instead of failing with a clear reason
    check(NonModalSettingsPolicy.isNonModalSettingsEnabledByAllPolicies()) {
      "Non-modal Settings is disabled, so the Settings dialog performance cannot be measured. " +
      "Enable it with the 'ide.ui.non.modal.settings.window' system property"
    }

    val token = SettingsDialogPerformanceTracker.start(spanNames.dialogShown, spanNames.pageReady, spanNames.configurableTreeBuilt)
    try {
      openNonModalSettings(context.project, mode, arguments)
      waitForSettingsReady(token)
      // the window is created together with the editor that reports the page as ready, so it is already shown here
      val window = findSettingsWindow() ?: error("Settings window was not found, although the Settings page was loaded")
      closeSettingsWindow(window)
    }
    finally {
      SettingsDialogPerformanceTracker.finish(token)
    }
  }

  private suspend fun openNonModalSettings(project: Project, mode: String, arguments: List<String>) {
    withContext(Dispatchers.EDT) {
      when (mode) {
        MODE_OPEN_DIALOG -> ShowSettingsAction.perform(project)
        MODE_OPEN_BY_ID -> {
          val id = arguments.parameter("id") ?: error("Missing configurable id")
          ShowSettingsUtilImpl.showSettingsDialog(project, id, null)
        }
        MODE_OPEN_BY_CLASS -> {
          val className = arguments.parameter("class") ?: error("Missing configurable class")
          @Suppress("UNCHECKED_CAST")
          val configurableClass = Class.forName(className).asSubclass(Configurable::class.java) as Class<Configurable>
          ShowSettingsUtil.getInstance().showSettingsDialog(project, configurableClass)
        }
        else -> error("Unsupported Settings dialog measurement mode: $mode")
      }
    }
  }

  private suspend fun waitForSettingsReady(token: SettingsDialogPerformanceTracker.MeasurementToken) {
    try {
      SettingsDialogPerformanceTracker.awaitPageReady(token, SETTINGS_READY_TIMEOUT)
    }
    catch (_: kotlinx.coroutines.TimeoutCancellationException) {
      error("Settings page was not loaded in $SETTINGS_READY_TIMEOUT")
    }
  }

  private suspend fun closeSettingsWindow(window: Window) {
    withContext(Dispatchers.EDT) {
      window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
    }
  }

  private suspend fun findSettingsWindow(): Window? = withContext(Dispatchers.EDT) {
    // The command intentionally measures only non-modal Settings. Modal Settings dialogs are deprecated.
    Window.getWindows().firstOrNull { window ->
      window.isShowing && SettingsNonModalDialog.isSettingsWindow(window)
    }
  }

  data class SpanNames(val dialogShown: String, val pageReady: String, val configurableTreeBuilt: String)
}

private fun List<String>.parameter(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0 && index + 1 < size) get(index + 1) else null
}