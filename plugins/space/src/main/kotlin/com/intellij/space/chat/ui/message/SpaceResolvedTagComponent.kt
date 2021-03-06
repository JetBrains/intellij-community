// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.message

import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime
import runtime.reactive.Property
import javax.swing.JComponent

internal fun createResolvedComponent(lifetime: Lifetime, resolved: Property<Boolean>): JComponent {
  val resolvedLabel = JBLabel(SpaceBundle.message("chat.message.resolved.text"), UIUtil.ComponentStyle.SMALL).apply {
    foreground = UIUtil.getContextHelpForeground()
    background = UIUtil.getPanelBackground()
    isOpaque = true
  }
  resolved.forEach(lifetime) {
    resolvedLabel.isVisible = it
  }
  return resolvedLabel
}