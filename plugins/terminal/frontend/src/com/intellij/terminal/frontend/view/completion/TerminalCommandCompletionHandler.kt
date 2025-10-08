package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionInitializationContextImpl
import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalOutputModel
import com.intellij.terminal.frontend.view.impl.toRelative
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor

internal class TerminalCommandCompletionHandler(
  private val completionType: CompletionType,
  private val invokedExplicitly: Boolean,
  autopopup: Boolean,
  synchronous: Boolean,
) : CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous) {
  fun invokeCompletion(e: AnActionEvent, time: Int) {
    val outputModel = e.terminalOutputModel ?: error("Output model is null during completion")
    val editor = e.terminalEditor ?: error("Terminal editor is null during completion")
    val project = editor.project ?: error("Project is null during completion")

    val inputEvent = e.inputEvent
    val caret = prepareCaret(editor, outputModel)

    val hasModifiers = inputEvent != null && inputEvent.modifiersEx != 0
    invokeCompletion(project, editor, time, hasModifiers, caret)
  }

  override fun invokeCompletion(
    project: Project,
    editor: Editor,
    time: Int,
    hasModifiers: Boolean,
    caret: Caret
  ) {
    var time = time

    val phase = CompletionServiceImpl.completionPhase
    val repeated = phase.indicator?.isRepeatedInvocation(completionType, editor) == true

    val newTime = phase.newCompletionStarted(time, repeated)
    if (invokedExplicitly) {
      time = newTime
    }
    val invocationCount = time
    if (CompletionServiceImpl.isPhase(CompletionPhase.InsertedSingleItem::class.java)) {
      CompletionServiceImpl.setCompletionPhase(CompletionPhase.NoCompletion)
    }
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.javaClass, CompletionPhase.CommittingDocuments::class.java)

    val startingTime = System.currentTimeMillis()
    val initCmd = Runnable {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      val context = CompletionInitializationContextImpl(editor, editor.caretModel.currentCaret, file, completionType, invocationCount)
      context.dummyIdentifier = ""
      doComplete(context, hasModifiers, true, startingTime)
    }
    try {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null, editor.getDocument())
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("completion.not.available.during.indexing"),
        DumbModeBlockedFunctionality.CodeCompletion)
      throw e
    }
  }

  private fun prepareCaret(commonEditor: Editor, outputModel: TerminalOutputModel): Caret {
    val caret = commonEditor.caretModel
    val primaryCaret = caret.primaryCaret
    primaryCaret.moveToOffset(outputModel.cursorOffset.toRelative(outputModel))
    clearCaretMarkers(commonEditor)
    markCaretAsProcessed(primaryCaret)
    return primaryCaret
  }
}
