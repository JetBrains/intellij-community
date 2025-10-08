// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.toolWindow.InternalDecoratorImpl.Companion.componentWithEditorBackgroundAdded
import com.intellij.toolWindow.InternalDecoratorImpl.Companion.componentWithEditorBackgroundRemoved
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TerminalToolWindowPanel : SimpleToolWindowPanel(false, true) {
  override fun addNotify() {
    super.addNotify()
    componentWithEditorBackgroundAdded(this)
  }

  override fun removeNotify() {
    super.removeNotify()
    componentWithEditorBackgroundRemoved(this)
  }
}