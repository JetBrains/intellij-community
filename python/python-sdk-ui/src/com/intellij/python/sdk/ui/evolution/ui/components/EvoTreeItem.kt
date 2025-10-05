@file:Suppress("removal", "DEPRECATION", "UnstableApiUsage")

package com.intellij.python.sdk.ui.evolution.ui.components

import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ListItem
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class EvoTreeItem(val element: EvoTreeElement, val separatorAbove: ListSeparator? = null) {
  val isSubstepSuppressed: Boolean
    get() = element is EvoTreeNodeElement && !Utils.isSubmenuSuppressed(element.presentation)

  val text: @ListItem String
    get() = element.presentation.text

  val secondaryText: @Nls String?
    get() = element.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)

  val icon: Icon?
    get() = element.presentation.icon

  val isEnabled: Boolean
    get() = element.isEnabled

  val keepPopupOnPerform: KeepPopupOnPerform
    get() = element.presentation.getKeepPopupOnPerform()

  val tooltip: @NlsContexts.Tooltip String?
    get() = element.presentation.getClientProperty(ActionUtil.TOOLTIP_TEXT)
}