package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.impl.ClientLookupManager
import com.intellij.codeInsight.lookup.impl.ClientLookupManagerBase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiUtilBase
import com.intellij.terminal.frontend.TerminalInput
import com.intellij.terminal.frontend.TerminalLookup
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalInput
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isSuppressCompletion

@ApiStatus.Internal
class TerminalCommandCompletionAction : BaseCodeCompletionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor!!
    if (!editor.isSuppressCompletion) {
      if (e.editor?.isReworkedTerminalEditor == true) {
        invokeCompletionGen2Terminal(e, CompletionType.BASIC, 1)
      }
      else {
        invokeCompletion(e, CompletionType.BASIC, 1)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.editor?.isPromptEditor == true
    if (e.editor?.isReworkedTerminalEditor == true) {
      e.presentation.isEnabledAndVisible = true
      //TODO("Check borders in last block")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private fun invokeCompletionGen2Terminal(e: AnActionEvent, type: CompletionType, time: Int) {
    val terminalInput = e.terminalInput
    val commonEditor = e.getData(CommonDataKeys.EDITOR)
    if (commonEditor == null) {
      TODO()
    }
    val project = commonEditor.getProject();
    if (project == null) {
      TODO()
    }

    val inputEvent = e.inputEvent;

    CodeCompletionHandlerBase.clearCaretMarkers(commonEditor)
    invokeCompletionGen2Terminal(project, commonEditor, time, inputEvent != null && inputEvent.modifiersEx != 0,
                                 commonEditor.getCaretModel().getPrimaryCaret(), type, terminalInput)
  }


  private fun invokeCompletionGen2Terminal(
    @NotNull project: Project,
    @NotNull editor: Editor,
    time: Int,
    hasModifiers: Boolean,
    @NotNull caret: Caret,
    completionType: CompletionType,
    terminalInput: TerminalInput?
  ) {
    var time = time
    val invokedExplicitly = true
    // autopopup is false?
    val autopopup = false
    val synchronous = true

    val handler = CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous)
    CodeCompletionHandlerBase.markCaretAsProcessed(caret)

    // invokedExplicitly == true
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
          CodeCompletionHandlerBase.calcSyncTimeOut(startingTime).toLong(), Computable {
            CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret, invocationCount, completionType,
                                                                               psiFile)
          })
      val hasValidContext = context != null
      if (!hasValidContext) {
        val psiFile = PsiUtilBase.getPsiFileInEditor(caret, project)
        context = CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount)
      }
      // перепробуем

      val mylookup = obtainLookup(editor, project, autopopup, terminalInput)
      val clientLookupManager = ClientLookupManager.getInstance(project.currentSession) as? ClientLookupManagerBase
      clientLookupManager?.putLookup(mylookup)

      // вот тут я должна добавить свой lookUp
      handler.doComplete(context, hasModifiers, hasValidContext, startingTime)
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

  private fun obtainLookup(editor: Editor, project: Project, autopopup: Boolean, terminalInput: TerminalInput?): LookupImpl {
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