@file:Suppress("removal", "DEPRECATION", "UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

import com.intellij.icons.AllIcons
import com.intellij.python.sdk.common.evolution.EvoNodeStats
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ListItem
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class EvoTreeItem(
  val element: EvoTreeElement,
  val separatorAbove: ListSeparator? = null,
  /** Full text behind [separatorAbove]'s elided label, shown while the header strip above this row is hovered. */
  val separatorTooltip: @NlsSafe String? = null,
) {
  val isSubstepSuppressed: Boolean
    get() = element is EvoTreeNodeElement && !Utils.isSubmenuSuppressed(element.presentation)

  val text: @ListItem String
    get() = element.presentation.text

  val secondaryText: @Nls String?
    get() = element.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)

  /**
   * True for rows that carry (or will lazily resolve) a secondary "version" text. The renderer reserves a fixed
   * width for that column up front so the popup is sized correctly on first show and never resizes when the version
   * arrives. See `EvoPopupListElementRenderer.reserveVersionColumn`.
   */
  val reservesVersionColumn: Boolean
    get() = secondaryText != null || (element as? EvoTreeLeafElement)?.action is EvoLazyDetail

  val icon: Icon?
    get() = element.presentation.icon

  val isEnabled: Boolean
    get() = element.isEnabled

  /**
   * True once this row can be acted on: a loaded row, or a tool node whose submenu can already be opened.
   *
   * A tool node is openable before its loader answers, because it shows an [EvoTreeMessageLeafElement] until the real
   * rows arrive. Only a node that failed, or answered with nothing, is closed to the user.
   */
  val isReady: Boolean
    get() = when (element) {
      is EvoTreeLazyNodeElement -> element.state != State.ERROR && element.state != State.NOT_AVAILABLE
      else -> element.state == State.DONE
    }

  /**
   * True when this row reports a load of its own.
   *
   * Never a tool node: a first load is reported on the "Loading…" row of the node's own submenu, and a reload in a
   * panel of its own opened in that submenu's place. A spinner on the tools of the main list is what made opening the
   * widget look busy (PY-91873), and a slow tool held that spinner for as long as it ran.
   */
  val showsLoader: Boolean
    get() = element.state == State.LOADING && element !is EvoTreeLazyNodeElement

  val keepPopupOnPerform: KeepPopupOnPerform
    get() = element.presentation.getKeepPopupOnPerform()

  val tooltip: @NlsContexts.Tooltip String?
    get() = element.presentation.getClientProperty(ActionUtil.TOOLTIP_TEXT)

  /** Opens this row's process output, for a row reporting a failure; null when it has no process behind it. */
  val showOutput: (() -> Unit)?
    get() = (element as? EvoTreeLazyNodeElement)?.showOutput

  /**
   * True when clicking this row opens the output of what it ran — a row that failed or came back with nothing, and has a
   * process behind it to show. Such a row does nothing else when clicked, which is why the whole of it is the target.
   */
  val opensProcessOutput: Boolean
    get() = showOutput != null && (element.state == State.ERROR || element.state == State.NOT_AVAILABLE)

  /** The panel that rebuilds this row's environment, or null when the row offers no rebuild — see [EvoBasePythonPanel]. */
  val basePythonPanel: EvoTreeNodeElement?
    get() = ((element as? EvoTreeLeafElement)?.action as? EvoBasePythonPanel)?.basePythonPanel

  /**
   * The sign a row that did not work carries, or null for one that did.
   *
   * Drawn in the row's trailing column rather than beside its text, so every sign in a list lines up with the submenu
   * arrows instead of trailing each label at whatever width it happens to have.
   */
  val statusIcon: Icon?
    get() = when (element.state) {
      State.ERROR -> AllIcons.General.Error
      // A tool that is simply unavailable, or answered with nothing, is not a failure — but it did not work either, so
      // it gets a sign of its own rather than looking like an ordinary disabled row with nothing to say.
      State.NOT_AVAILABLE -> AllIcons.General.Warning
      State.CREATED, State.LOADING, State.DONE -> null
    }

  /** True when this row reveals more of the list rather than selecting an environment — see [EvoLinkRow]. */
  val isLinkRow: Boolean
    get() = (element as? EvoTreeLeafElement)?.action is EvoLinkRow
}

/**
 * The statistics identity of the node this row stands for, or null when the row is not a lazily-loaded node.
 *
 * Only the tool nodes carry one — a leaf belongs to whichever node listed it, which the row itself does not know — so
 * a control reported against a non-node row is reported without a node rather than against a guessed one.
 */
internal fun EvoTreeItem.evoNodeStats(): EvoNodeStats? = (element as? EvoTreeLazyNodeElement)?.nodeStats

