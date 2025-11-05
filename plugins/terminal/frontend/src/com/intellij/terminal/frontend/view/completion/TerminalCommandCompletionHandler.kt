package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionPhase.CommittingDocuments
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupArranger.DefaultArranger
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.frontend.action.TerminalFrontendDataContextUtils.terminalOutputModel
import com.intellij.terminal.frontend.view.impl.toRelative
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.view.TerminalOutputModel

internal class TerminalCommandCompletionHandler(
  private val completionType: CompletionType,
  private val invokedExplicitly: Boolean,
  private val autopopup: Boolean,
  private val synchronous: Boolean,
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
    caret: Caret,
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
    CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.javaClass, CommittingDocuments::class.java)

    val startingTime = System.currentTimeMillis()
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
    val context = CompletionInitializationContextImpl(editor, editor.caretModel.currentCaret, file, completionType, invocationCount)
    context.dummyIdentifier = ""
    doComplete(context, hasModifiers, startingTime)
  }

  private fun doComplete(
    initContext: CompletionInitializationContextImpl,
    hasModifiers: Boolean,
    startingTime: Long,
  ) {
    val editor = initContext.editor
    val lookup = obtainLookup(editor, initContext.project)

    val phase = CompletionServiceImpl.completionPhase
    if (phase is CommittingDocuments) {
      phase.indicator?.closeAndFinish(false)
      phase.replaced = true
    }
    else {
      CompletionServiceImpl.assertPhase(CompletionPhase.NoCompletion.javaClass)
    }

    val indicator = CompletionProgressIndicator(editor, initContext.caret,
                                                initContext.invocationCount, this,
                                                initContext.offsetMap,
                                                initContext.hostOffsets,
                                                hasModifiers, lookup)
    val arranger = TerminalCompletionLookupArranger(indicator)
    indicator.setLookupArranger(arranger)

    if (synchronous) {
      trySynchronousCompletion(initContext, hasModifiers, startingTime, indicator, initContext.hostOffsets)
    }
    else {
      scheduleContributorsAfterAsyncCommit(initContext, indicator, hasModifiers)
    }
  }

  private fun obtainLookup(editor: Editor, project: Project): LookupImpl {
    val existing = LookupManager.getActiveLookup(editor) as? LookupImpl
    if (existing != null && existing.isCompletion) {
      existing.markReused()
      if (!autopopup) {
        existing.setLookupFocusDegree(LookupFocusDegree.FOCUSED)
      }
      return existing
    }

    val arranger = object : DefaultArranger() {
      override fun isCompletion(): Boolean {
        return true
      }
    }
    val lookup = LookupManager.getInstance(project).createLookup(editor, LookupElement.EMPTY_ARRAY, "", arranger) as LookupImpl
    lookup.setLookupFocusDegree(if (autopopup) LookupFocusDegree.UNFOCUSED else LookupFocusDegree.FOCUSED)
    return lookup
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
