package com.intellij.terminal.frontend.editor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.impl.getTitleText
import com.intellij.testFramework.LightVirtualFile

internal class TerminalViewVirtualFile(val tab: TerminalToolWindowTab) : LightVirtualFile(tab.view.getTitleText()) {
  init {
    fileType = TerminalViewFileType
    isWritable = true // to be able to rename the file
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }
}