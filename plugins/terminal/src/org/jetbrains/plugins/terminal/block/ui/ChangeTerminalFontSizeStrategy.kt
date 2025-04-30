// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.actions.ChangeEditorFontSizeStrategy
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import java.awt.Point

@ApiStatus.Internal
object ChangeTerminalFontSizeStrategy: ChangeEditorFontSizeStrategy {
  private val service: TerminalFontSizeProviderImpl get() = TerminalFontSizeProviderImpl.getInstance()

  override var fontSize: Float
    get() = service.getFontSize()
    set(value) {
      service.setFontSize(value)
    }

  override val defaultFontSize: Float
    get() = service.getDefaultScaledFontSize().floatValue

  @Suppress("DialogTitleCapitalization")
  override val defaultFontSizeText: String
    get() = IdeBundle.message("action.reset.font.size", defaultFontSize)

  override val overridesChangeFontSizeActions: Boolean = true

  override fun preferredZoomPointRelative(editor: EditorImpl): Point = editor.bottomLeftCornerOrNull() ?: Point()
}


private fun EditorImpl.bottomLeftCornerOrNull(): Point? =
  if (component.isShowing) {
    Point(0, scrollingModel.visibleArea.height)
  }
  else {
    null
  }
