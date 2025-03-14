package com.intellij.terminal.frontend

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.impl.ClientLookupManager
import com.intellij.codeInsight.lookup.impl.ClientLookupManagerBase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileEx
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.outputModelImpl
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalInput
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelImpl

class TerminalCodeCompletionBase(
  val completionType: CompletionType,
  val invokedExplicitly: Boolean,
  val autopopup: Boolean,
  val synchronous: Boolean,
) :
  CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous) {
  fun invokeCompletion(e: AnActionEvent, time: Int) {
    val outputModel = e.outputModelImpl
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
    @NotNull project: Project,
    @NotNull editor: Editor,
    time: Int,
    hasModifiers: Boolean,
    @NotNull caret: Caret,
    terminalInput: TerminalInput,
  ) {
    var time = time
    StatisticsUpdate.Companion.applyLastCompletionStatisticsUpdate()

    val app = ApplicationManager.getApplication()
    if (!app.isUnitTestMode() && app.isWriteAccessAllowed()) {
      throw AssertionError("Completion should not be invoked inside write action")
    }

    CompletionAssertions.checkEditorValid(editor)

    val offset = editor.getCaretModel().offset
    if (editor.getDocument().getRangeGuard(offset, offset) != null) {
      editor.getDocument().fireReadOnlyModificationAttempt()
      EditorModificationUtil.checkModificationAllowed(editor)
      return
    }

    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return
    }

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

    if (invocationCount > 1) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION)
    }

    val startingTime = System.currentTimeMillis()
    val initCmd = Runnable {
      WriteAction.run<RuntimeException> { EditorUtil.fillVirtualSpaceUntilCaret(editor) }

      val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
        "command_output",
        PlainTextLanguage.INSTANCE,
        ""
      )

      var context: CompletionInitializationContextImpl? =
        ProgressIndicatorUtils.withTimeout<CompletionInitializationContextImpl?>(
          calcSyncTimeOut(startingTime).toLong(), Computable {
            PsiDocumentManager.getInstance(project).commitAllDocuments()

            psiFile.putUserData<Boolean?>(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)
            CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount, psiFile.language)
          })
      val hasValidContext = context != null
      if (!hasValidContext) {
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

  private fun prepareCaret(commonEditor: Editor, outputModel: TerminalOutputModelImpl): Caret {
    val caret = commonEditor.caretModel
    val primaryCaret = caret.primaryCaret
    primaryCaret.moveToOffset(outputModel.cursorOffsetState.value)
    clearCaretMarkers(commonEditor)
    markCaretAsProcessed(primaryCaret)
    return primaryCaret
  }

  private fun obtainLookup(editor: Editor, project: Project, autopopup: Boolean, terminalInput: TerminalInput): LookupImpl {
    CompletionAssertions.checkEditorValid(editor)

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
