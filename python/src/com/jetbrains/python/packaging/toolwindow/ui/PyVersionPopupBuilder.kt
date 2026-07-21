// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import javax.swing.Icon

private const val MAX_VISIBLE_ROWS = 12

/**
 * Builds the platform list popup for the version dropdowns. Uses [JBPopupFactory.createListPopup]
 * to inherit the standard look: bubble-style speed search above the list, no red "no hits"
 * highlight. Speed-search matching is the platform default (case-insensitive substring) —
 * adequate for a version list a few dozen entries long.
 */
internal fun buildVersionChooserPopup(
  items: List<String>,
  iconFor: (String) -> Icon? = { null },
  onChosen: (String) -> Unit,
): ListPopup {
  val step = object : BaseListPopupStep<String>(null, items) {
    override fun isSpeedSearchEnabled(): Boolean = true
    override fun getIconFor(value: String): Icon? = iconFor(value)
    override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? =
      doFinalStep { selectedValue?.let(onChosen) }
  }
  return JBPopupFactory.getInstance().createListPopup(step, MAX_VISIBLE_ROWS)
}
