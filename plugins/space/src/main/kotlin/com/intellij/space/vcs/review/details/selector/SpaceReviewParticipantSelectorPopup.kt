// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Pair
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

internal fun showPopup(
  parent: JComponent,
  component: JComponent,
  focusableComponent: JComponent,
  clickListener: ActionListener,
) {

  JBPopupFactory.getInstance().createComponentPopupBuilder(component, focusableComponent)
    .setRequestFocus(true)
    .setCancelOnClickOutside(true)
    .setResizable(true)
    .setMovable(true)
    .setKeyboardActions(listOf(Pair.create(clickListener, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
    .createPopup()
    .showUnderneathOf(parent)
}
