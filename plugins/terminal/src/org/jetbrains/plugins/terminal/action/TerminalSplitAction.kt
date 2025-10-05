// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.idea.ActionsBundle
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidgetListener
import com.intellij.util.ui.UIUtil
import com.jediterm.terminal.ui.TerminalAction
import com.jediterm.terminal.ui.TerminalActionPresentation
import java.awt.event.KeyEvent

class TerminalSplitAction private constructor(presentation: TerminalActionPresentation,
                                              private val vertically: Boolean,
                                              private val listener: JBTerminalWidgetListener?) : TerminalAction(presentation) {
  override fun actionPerformed(e: KeyEvent?): Boolean {
    listener!!.split(vertically)
    return true
  }

  override fun isEnabled(e: KeyEvent?): Boolean {
    return listener != null && listener.canSplit(vertically)
  }

  companion object {
    @JvmStatic
    fun create(vertically: Boolean, listener: JBTerminalWidgetListener?): TerminalSplitAction {
      val text = if (vertically)
        ActionsBundle.message("action.SplitVertically.text")
      else
        ActionsBundle.message("action.SplitHorizontally.text")
      val keyStrokes = JBTerminalSystemSettingsProviderBase.getKeyStrokesByActionId(
        if (vertically) "TW.SplitRight" else "TW.SplitDown")
      return TerminalSplitAction(TerminalActionPresentation(UIUtil.removeMnemonic(text), keyStrokes), vertically, listener)
    }
  }
}