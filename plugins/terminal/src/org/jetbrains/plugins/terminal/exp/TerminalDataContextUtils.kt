// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptController
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptModel

@ApiStatus.Experimental
object TerminalDataContextUtils {
  internal val IS_PROMPT_EDITOR_KEY: Key<Boolean> = Key.create("PromptEditor")
  internal val IS_OUTPUT_EDITOR_KEY: Key<Boolean> = Key.create("OutputEditor")
  internal val IS_ALTERNATE_BUFFER_EDITOR_KEY: Key<Boolean> = Key.create("AlternateBufferEditor")

  val Editor.isPromptEditor: Boolean
    get() = getUserData(IS_PROMPT_EDITOR_KEY) == true
  val Editor.isOutputEditor: Boolean
    get() = getUserData(IS_OUTPUT_EDITOR_KEY) == true
  val Editor.isAlternateBufferEditor: Boolean
    get() = getUserData(IS_ALTERNATE_BUFFER_EDITOR_KEY) == true
  internal val Editor.terminalPromptModel: TerminalPromptModel?
    get() = getUserData(TerminalPromptModel.KEY)

  val DataContext.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)
  internal val DataContext.outputController: TerminalOutputController?
    get() = getData(TerminalOutputController.KEY)
  internal val DataContext.promptController: TerminalPromptController?
    get() = getData(TerminalPromptController.KEY)
  internal val DataContext.simpleTerminalController: SimpleTerminalController?
    get() = getData(SimpleTerminalController.KEY)
  internal val DataContext.blockTerminalController: BlockTerminalController?
    get() = getData(BlockTerminalController.KEY)
  internal val DataContext.selectionController: TerminalSelectionController?
    get() = getData(TerminalSelectionController.KEY)
  internal val DataContext.terminalFocusModel: TerminalFocusModel?
    get() = getData(TerminalFocusModel.KEY)
  internal val DataContext.terminalSession: BlockTerminalSession?
    get() = getData(BlockTerminalSession.DATA_KEY)

  val AnActionEvent.editor: Editor?
    get() = getData(CommonDataKeys.EDITOR)
  internal val AnActionEvent.outputController: TerminalOutputController?
    get() = getData(TerminalOutputController.KEY)
  internal val AnActionEvent.promptController: TerminalPromptController?
    get() = getData(TerminalPromptController.KEY)
  internal val AnActionEvent.simpleTerminalController: SimpleTerminalController?
    get() = getData(SimpleTerminalController.KEY)
  internal val AnActionEvent.blockTerminalController: BlockTerminalController?
    get() = getData(BlockTerminalController.KEY)
  internal val AnActionEvent.selectionController: TerminalSelectionController?
    get() = getData(TerminalSelectionController.KEY)
  internal val AnActionEvent.terminalFocusModel: TerminalFocusModel?
    get() = getData(TerminalFocusModel.KEY)
  internal val AnActionEvent.terminalSession: BlockTerminalSession?
    get() = getData(BlockTerminalSession.DATA_KEY)
}