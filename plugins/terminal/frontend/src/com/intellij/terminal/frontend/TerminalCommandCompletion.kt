package com.intellij.terminal.frontend

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.impl.ClientLookupManager
import com.intellij.codeInsight.lookup.impl.ClientLookupManagerBase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiUtilBase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.withTimeout
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalOutputModel
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalInput
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel

internal class TerminalCommandCompletion(
  val completionType: CompletionType,
  val invokedExplicitly: Boolean,
  val autopopup: Boolean,
  val synchronous: Boolean,
) :
  CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous) {
  fun invokeCompletion(e: AnActionEvent, time: Int) {
    val outputModel = e.terminalOutputModel
                      ?: throw AssertionError("Output model is null during completion")

    val commonEditor = e.getData(CommonDataKeys.EDITOR)
                       ?: throw AssertionError("Common editor is null during completion")

    val project = commonEditor.project
                  ?: throw AssertionError("Project is null during completion")

    val terminalInput = e.terminalInput
                        ?: throw AssertionError("Terminal input is null during completion")

    val inputEvent = e.inputEvent
    val caret = prepareCaret(commonEditor, outputModel)

    invokeCompletion(project, commonEditor, time, inputEvent != null && inputEvent.modifiersEx != 0,
                     caret, terminalInput)
  }

  private fun invokeCompletion(
    project: Project,
    editor: Editor,
    time: Int,
    hasModifiers: Boolean,
    caret: Caret,
    terminalInput: TerminalInput,
  ) {
    var time = time

    val phase = CompletionServiceImpl.Companion.completionPhase
    val repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(completionType, editor)

    val newTime = phase.newCompletionStarted(time, repeated)
    if (invokedExplicitly) {
      time = newTime
    }
    val invocationCount = time
    if (CompletionServiceImpl.Companion.isPhase(CompletionPhase.InsertedSingleItem::class.java)) {
      CompletionServiceImpl.Companion.setCompletionPhase(CompletionPhase.NoCompletion)
    }
    CompletionServiceImpl.Companion.assertPhase(CompletionPhase.NoCompletion.javaClass, CompletionPhase.CommittingDocuments::class.java)

    val startingTime = System.currentTimeMillis()
    val initCmd = Runnable {
      var context: CompletionInitializationContextImpl? =
        withTimeout<CompletionInitializationContextImpl?>(calcSyncTimeOut(startingTime).toLong(), Computable {
          CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret, invocationCount, completionType)
        })
      val hasValidContext = context != null
      if (!hasValidContext) {
        val psiFile = PsiUtilBase.getPsiFileInEditor(caret, project)
        context = CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount)
      }

      val terminalLookup = obtainLookup(editor, project, autopopup, terminalInput)
      val clientLookupManager = ClientLookupManager.getInstance(project.currentSession) as? ClientLookupManagerBase
      clientLookupManager?.putLookup(terminalLookup)

      doComplete(context, hasModifiers, hasValidContext, startingTime)
    }
    try {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null, editor.getDocument())
    }
    catch (e: IndexNotReadyException) {
      DumbService.Companion.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("completion.not.available.during.indexing"),
        DumbModeBlockedFunctionality.CodeCompletion)
      throw e
    }
  }

  private fun prepareCaret(commonEditor: Editor, outputModel: TerminalOutputModel): Caret {
    val caret = commonEditor.caretModel
    val primaryCaret = caret.primaryCaret
    primaryCaret.moveToOffset(outputModel.cursorOffsetState.value)
    clearCaretMarkers(commonEditor)
    markCaretAsProcessed(primaryCaret)
    return primaryCaret
  }

  private fun obtainLookup(editor: Editor, project: Project, autopopup: Boolean, terminalInput: TerminalInput): LookupImpl {
    val session = project.currentSession
    val lookup = TerminalLookup(session, editor, DefaultArranger(), terminalInput) as LookupImpl

    if (editor.isOneLineMode()) {
      lookup.setCancelOnClickOutside(true)
      lookup.setCancelOnOtherWindowOpen(true)
    }
    lookup.setLookupFocusDegree(if (autopopup) LookupFocusDegree.UNFOCUSED else LookupFocusDegree.FOCUSED)

    return lookup
  }

}
