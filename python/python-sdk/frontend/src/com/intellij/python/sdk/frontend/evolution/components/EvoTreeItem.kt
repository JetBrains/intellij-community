@file:Suppress("removal", "DEPRECATION", "UnstableApiUsage")

package com.intellij.python.sdk.frontend.evolution.components

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