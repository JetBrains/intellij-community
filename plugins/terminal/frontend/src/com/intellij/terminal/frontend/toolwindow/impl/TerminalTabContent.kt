package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.terminal.frontend.toolwindow.getTerminalTab
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareFullTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.buildSettingsAwareTitle
import org.jetbrains.plugins.terminal.util.TerminalTitleUtils.stateFlow

/**
 * Common interface for features that should work both with Classic and Reworked Terminal tabs.
 *
 * Use [toTerminalTabContent] or [toTerminalTabContentOrNull] to convert [Content] to [TerminalTabContent].
 */
internal sealed interface TerminalTabContent {
  val content: Content

  @NlsSafe
  fun getTabTitle(): String

  @NlsSafe
  fun getFullTabTitle(): String

  /**
   * Emitted every time the terminal tab title changes.
   */
  fun titleUpdatesFlow(): Flow<Unit>

  /**
   * Checks whether confirmation is required when closing the terminal tab.
   * Returns `null` if no confirmation is required.
   */
  suspend fun getClosingConfirmationDetails(): ClosingConfirmationDetails?

  class Reworked(override val content: Content, val view: TerminalView) : TerminalTabContent {
    override fun getTabTitle(): String = view.getTitleText()

    override fun getFullTabTitle(): String = view.getFullTitleText()

    override fun titleUpdatesFlow(): Flow<Unit> = view.titleStateFlow().map { }

    override suspend fun getClosingConfirmationDetails(): ClosingConfirmationDetails? {
      return if (TerminalTabCloseListenerImpl.shouldConfirmClosing(view)) {
        ClosingConfirmationDetails(content, view.getFullTitleText())
      }
      else {
        null
      }
    }
  }

  class Classic(override val content: Content, val widget: TerminalWidget) : TerminalTabContent {
    override fun getTabTitle(): String = widget.terminalTitle.buildSettingsAwareTitle()

    override fun getFullTabTitle(): String = widget.terminalTitle.buildSettingsAwareFullTitle()

    override fun titleUpdatesFlow(): Flow<Unit> = widget.terminalTitle.stateFlow(
      buildCroppedTitle = { it.buildSettingsAwareTitle() },
      buildFullTitle = { it.buildSettingsAwareFullTitle() },
    ).map { }

    override suspend fun getClosingConfirmationDetails(): ClosingConfirmationDetails? = withContext(Dispatchers.IO) {
      if (widget.isCommandRunning()) {
        ClosingConfirmationDetails(content, widget.terminalTitle.buildFullTitle())
      }
      else {
        null
      }
    }
  }

  data class ClosingConfirmationDetails(
    val content: Content,
    val fullTitle: String,
  )
}

/**
 * Tries to convert [Content] to [TerminalTabContent].
 * @return `null` if the content is nor Reworked nor Classic Terminal tab.
 */
internal fun Content.toTerminalTabContentOrNull(): TerminalTabContent? {
  getTerminalTab()?.view?.let {
    return TerminalTabContent.Reworked(this, it)
  }

  TerminalToolWindowManager.findWidgetByContent(this)?.let {
    return TerminalTabContent.Classic(this, it)
  }

  return null
}

/**
 * Tries to convert [Content] to [TerminalTabContent].
 * @throws IllegalStateException if the content is nor Reworked nor Classic Terminal tab.
 */
@Throws(IllegalStateException::class)
internal fun Content.toTerminalTabContent(): TerminalTabContent {
  return toTerminalTabContentOrNull() ?: error("Content $this is not a terminal tab")
}

internal fun Content.isTerminalTabContent(): Boolean {
  return toTerminalTabContentOrNull() != null
}