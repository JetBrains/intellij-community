package com.intellij.terminal.frontend.editor

import com.intellij.terminal.frontend.toolwindow.impl.getTitleText
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.testFramework.LightVirtualFile

internal class TerminalViewVirtualFile(
  val terminalView: TerminalView,
  val closeOnProcessTermination: Boolean,
) : LightVirtualFile(terminalView.getTitleText()) {
  init {
    fileType = TerminalViewFileType
    isWritable = true // to be able to rename the file
  }
}