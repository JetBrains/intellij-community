@file:Suppress("removal", "DEPRECATION", "UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

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
   * A tool node's load is reported inside its submenu instead, on its "Loading…" row: a spinner on every tool of the
   * main list is what made opening the widget look busy (PY-91873). A reload the user asked for is the exception — see
   * [EvoTreeLazyNodeElement.isReloading].
   */
  val showsLoader: Boolean
    get() = element.state == State.LOADING && (element !is EvoTreeLazyNodeElement || element.isReloading)

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

  /** The finer choices this row stands for, or null when it stands only for itself — see [EvoAlternatives]. */
  val alternatives: EvoAlternatives?
    get() = ((element as? EvoTreeLeafElement)?.action as? EvoAlternatives)?.takeIf { it.alternatives.size > 1 }
}

/**
 * The statistics identity of the node this row stands for, or null when the row is not a lazily-loaded node.
 *
 * Only the tool nodes carry one — a leaf belongs to whichever node listed it, which the row itself does not know — so
 * a control reported against a non-node row is reported without a node rather than against a guessed one.
 */
internal fun EvoTreeItem.evoNodeStats(): EvoNodeStats? = (element as? EvoTreeLazyNodeElement)?.nodeStats

