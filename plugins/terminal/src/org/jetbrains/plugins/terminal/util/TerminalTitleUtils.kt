// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.util

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.TerminalTitle
import com.intellij.terminal.TerminalTitleListener
import com.intellij.util.text.UniqueNameGenerator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.settings.TerminalApplicationTitleShowingMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object TerminalTitleUtils {
  /**
   * Builds a title string taking into account the "Show Application Title" Terminal setting.
   *
   * @param isCommandRunning used for determining whether to show an application title when a command is running.
   * Taken into account only if "Show application title in tab name: when command is running" option is enabled
   * ([TerminalOptionsProvider.applicationTitleShowingMode]).
   */
  @JvmStatic
  @ApiStatus.Experimental
  fun TerminalTitle.buildSettingsAwareTitle(isCommandRunning: Boolean = false): @Nls String {
    return buildTitle(ignoreAppTitle = !shouldShowAppTitle(isCommandRunning))
  }

  /**
   * Builds a title string taking into account the "Show Application Title" Terminal setting.
   *
   * @param isCommandRunning used for determining whether to show an application title when a command is running.
   * Taken into account only if "Show application title in tab name: when command is running" option is enabled
   * ([TerminalOptionsProvider.applicationTitleShowingMode]).
   */
  @JvmStatic
  @ApiStatus.Experimental
  fun TerminalTitle.buildSettingsAwareFullTitle(isCommandRunning: Boolean = false): @Nls String {
    return buildFullTitle(ignoreAppTitle = !shouldShowAppTitle(isCommandRunning))
  }

  private fun shouldShowAppTitle(isCommandRunning: Boolean): Boolean {
    val options = TerminalOptionsProvider.instance
    return options.showApplicationTitle
           && (options.applicationTitleShowingMode == TerminalApplicationTitleShowingMode.WHEN_COMMAND_RUNNING && isCommandRunning
               || options.applicationTitleShowingMode == TerminalApplicationTitleShowingMode.ALWAYS)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun createDefaultTabName(
    toolWindow: ToolWindow,
    defaultName: String = TerminalOptionsProvider.instance.tabName,
  ): @NlsSafe String {
    val existingNames = toolWindow.contentManager.contentsRecursively.map { it.displayName }
    return UniqueNameGenerator.generateUniqueName(
      defaultName,
      "",
      "",
      " (",
      ")",
      Condition { !existingNames.contains(it) }
    )
  }

  @ApiStatus.Internal
  fun TerminalTitle.stateFlow(buildTitle: (TerminalTitle) -> String): Flow<TitleData> {
    fun titleData(title: TerminalTitle): TitleData {
      return TitleData(buildTitle(title), title.defaultTitle, title.userDefinedTitle)
    }

    val flow = channelFlow {
      val disposable = Disposer.newDisposable()
      addTitleListener(object : TerminalTitleListener {
        override fun onTitleChanged(terminalTitle: TerminalTitle) {
          trySend(titleData(terminalTitle))
        }
      }, disposable)

      send(titleData(this@stateFlow))

      awaitClose { Disposer.dispose(disposable) }
    }

    return flow.distinctUntilChanged()
  }

  @ApiStatus.Internal
  val TITLE_UPDATE_DELAY: Duration = 300.milliseconds

  @ApiStatus.Internal
  data class TitleData(
    @param:NlsSafe val text: String,
    val defaultName: String?,
    val userDefinedName: String?,
  )
}