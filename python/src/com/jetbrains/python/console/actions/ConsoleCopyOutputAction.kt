// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.PythonConsoleView
import java.awt.datatransfer.StringSelection

class ConsoleCopyOutputAction(private val consoleView: PythonConsoleView): AnAction(PyBundle.message("pydev.console.runner.copy.console.output.text"),
                                                                                    PyBundle.message("pydev.console.runner.copy.console.output.description"),
                                                                                    AllIcons.Actions.Copy) {
  override fun actionPerformed(e: AnActionEvent) {
    val content = consoleView.historyViewer.document.charsSequence.toString()
    CopyPasteManager.getInstance().setContents(StringSelection(content))
  }
}