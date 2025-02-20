// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase.calcSyncTimeOut
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase.markCaretAsProcessed
import com.intellij.codeInsight.completion.CompletionPhase.CommittingDocuments
import com.intellij.codeInsight.completion.CompletionPhase.InsertedSingleItem
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.assertPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.completionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.isPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.setCompletionPhase
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.withTimeout
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor

@ApiStatus.Internal
class TerminalCommandCompletionAction : BaseCodeCompletionAction(), ActionRemoteBehaviorSpecification.Disabled {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.editor!!
    if (editor.getUserData(SUPPRESS_COMPLETION) != true) {
      if (e.editor?.isReworkedTerminalEditor == true) {
        invokeCompletionGen2Terminal(e, CompletionType.BASIC, 1)
      } else {
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

  companion object {
    val SUPPRESS_COMPLETION: Key<Boolean> = Key.create("SUPPRESS_TERMINAL_COMPLETION")
  }

  private fun invokeCompletionGen2Terminal(e: AnActionEvent, type: CompletionType, time: Int) {
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
    invokeCompletionGen2Terminal(project, commonEditor, time, inputEvent != null && inputEvent.modifiersEx != 0, commonEditor.getCaretModel().getPrimaryCaret(), type)
  }

  private fun invokeCompletionGen2Terminal(@NotNull project: Project, @NotNull editor: Editor, time: Int, hasModifiers: Boolean, @NotNull caret: Caret, completionType: CompletionType) {
    var time = time
    val invokedExplicitly = true
    // autopopup is false?
    val autopopup = false
    val synchronous = true

    val handler = CodeCompletionHandlerBase(completionType, invokedExplicitly, autopopup, synchronous)
    markCaretAsProcessed(caret)

    // invokedExplicitly == true
    StatisticsUpdate.applyLastCompletionStatisticsUpdate()

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

    val phase = completionPhase
    val repeated = phase.indicator != null && phase.indicator.isRepeatedInvocation(completionType, editor)

    val newTime = phase.newCompletionStarted(time, repeated)
    if (invokedExplicitly) {
      time = newTime
    }
    val invocationCount = time
    if (isPhase(InsertedSingleItem::class.java)) {
      setCompletionPhase(CompletionPhase.NoCompletion)
    }
    assertPhase(CompletionPhase.NoCompletion.javaClass, CommittingDocuments::class.java)

    if (invocationCount > 1) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.SECOND_BASIC_COMPLETION)
    }

    val startingTime = System.currentTimeMillis()
    val initCmd = Runnable {
      WriteAction.run<RuntimeException> { EditorUtil.fillVirtualSpaceUntilCaret(editor) }

      var context: CompletionInitializationContextImpl? =
        withTimeout<CompletionInitializationContextImpl?>(calcSyncTimeOut(startingTime).toLong(), Computable {
          CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret, invocationCount, completionType)
        })
      val hasValidContext = context != null
      if (!hasValidContext) {
        val psiFile = PsiUtilBase.getPsiFileInEditor(caret, project)
        context = CompletionInitializationContextImpl(editor, caret, psiFile, completionType, invocationCount)
      }

      handler.doComplete(context, hasModifiers, hasValidContext, startingTime)
    }
    try {
      CommandProcessor.getInstance().executeCommand(project, initCmd, null, null, editor.getDocument())
    }
    catch (e: IndexNotReadyException) {
      getInstance(project).showDumbModeNotificationForFunctionality(CodeInsightBundle.message("completion.not.available.during.indexing"),
                                                                    DumbModeBlockedFunctionality.CodeCompletion)
      throw e
    }
  }
}
