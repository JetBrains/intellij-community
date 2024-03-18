// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

object TerminalDataContextUtils {
  val IS_PROMPT_EDITOR_KEY: Key<Boolean> = Key.create("PromptEditor")
  val IS_OUTPUT_EDITOR_KEY: Key<Boolean> = Key.create("OutputEditor")
  val IS_ALTERNATE_BUFFER_EDITOR_KEY: Key<Boolean> = Key.create("AlternateBufferEditor")

  val Editor.isPromptEditor: Boolean
    get() = getUserData(IS_PROMPT_EDITOR_KEY) == true
  val Editor.isOutputEditor: Boolean
    get() = getUserData(IS_OUTPUT_EDITOR_KEY) == true
  val Editor.isAlternateBufferEditor: Boolean
    get() = getUserData(IS_ALTERNATE_BUFFER_EDITOR_KEY) == true

  val DataContext.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)
  val DataContext.outputController: TerminalOutputController?
    get() = getData(TerminalOutputController.KEY)
  val DataContext.promptController: TerminalPromptController?
    get() = getData(TerminalPromptController.KEY)
  val DataContext.simpleTerminalController: SimpleTerminalController?
    get() = getData(SimpleTerminalController.KEY)
  val DataContext.blockTerminalController: BlockTerminalController?
    get() = getData(BlockTerminalController.KEY)
  val DataContext.selectionController: TerminalSelectionController?
    get() = getData(TerminalSelectionController.KEY)
  val DataContext.terminalFocusModel: TerminalFocusModel?
    get() = getData(TerminalFocusModel.KEY)
  val DataContext.terminalSession: BlockTerminalSession?
    get() = getData(BlockTerminalSession.DATA_KEY)

  val AnActionEvent.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)
  val AnActionEvent.outputController: TerminalOutputController?
    get() = getData(TerminalOutputController.KEY)
  val AnActionEvent.promptController: TerminalPromptController?
    get() = getData(TerminalPromptController.KEY)
  val AnActionEvent.simpleTerminalController: SimpleTerminalController?
    get() = getData(SimpleTerminalController.KEY)
  val AnActionEvent.blockTerminalController: BlockTerminalController?
    get() = getData(BlockTerminalController.KEY)
  val AnActionEvent.selectionController: TerminalSelectionController?
    get() = getData(TerminalSelectionController.KEY)
  val AnActionEvent.terminalFocusModel: TerminalFocusModel?
    get() = getData(TerminalFocusModel.KEY)
  val AnActionEvent.terminalSession: BlockTerminalSession?
    get() = getData(BlockTerminalSession.DATA_KEY)
}