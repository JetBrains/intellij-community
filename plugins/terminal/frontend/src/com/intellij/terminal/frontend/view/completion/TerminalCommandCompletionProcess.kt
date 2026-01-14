package com.intellij.terminal.frontend.view.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.CompletionSorterImpl
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiDocumentManager
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.toRelative
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration
import java.util.function.Supplier
import javax.swing.Icon

/**
 * Contains the data related to the terminal completion session and methods for manipulating it,
 * such as [addItems], [tryInsertOrShowPopup], [cancel], etc.
 *
 * Have to inherit [CompletionProcessEx] to be compatible with platform completion APIs like [BaseCompletionLookupArranger].
 *
 * The lifecycle of the process is bound to the [coroutineScope].
 * It starts when the completion popup ([LookupImpl]) is created (and passed to the constructor) and continues until it is closed.
 * [cancel] method should be used to terminate the process.
 */
@OptIn(AwaitCancellationAndInvoke::class)
internal class TerminalCommandCompletionProcess(
  val context: TerminalCommandCompletionContext,
  private val lookup: LookupImpl,
  private val coroutineScope: CoroutineScope,  // The lifecycle
) : CompletionProcessEx, UserDataHolderBase() {
  private var arranger: CompletionLookupArrangerImpl? = lookup.arranger as? CompletionLookupArrangerImpl
  private val parameters: CompletionParameters

  private var restartOnPrefixChange = false
  var restartPending = false
    private set

  init {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: error("Can't find PSI file for ${editor.document}")
    val offset = editor.caretModel.offset.coerceIn(0, editor.document.textLength - 1)
    val element = psiFile.findElementAt(0)!!  // TerminalOutput PSI file has a single leaf element
    parameters = CompletionParameters(element, psiFile, CompletionType.BASIC,
                                      offset, 1, editor, this)

    lookup.isCalculating = true

    val lookupListener = object : LookupListener {
      override fun lookupCanceled(event: LookupEvent) {
        cancel()
      }
    }
    lookup.addLookupListener(lookupListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      lookup.removeLookupListener(lookupListener)
    }
  }

  override fun getProject(): Project {
    return context.project
  }

  override fun getEditor(): Editor {
    return context.editor
  }

  override fun getLookup(): LookupImpl {
    return lookup
  }

  override fun getCaret(): Caret {
    return editor.caretModel.primaryCaret
  }

  override fun isAutopopupCompletion(): Boolean {
    return context.isAutoPopup
  }

  override fun getParameters(): CompletionParameters {
    return parameters
  }

  fun setLookupArranger(arranger: CompletionLookupArrangerImpl) {
    this.arranger = arranger
    lookup.setArranger(arranger)
  }

  fun addItems(items: List<CompletionResult>) {
    if (lookup.isLookupDisposed) {
      return
    }

    val curArranger = arranger ?: error("CompletionLookupArrangerImpl is null")
    curArranger.batchUpdate {
      for (item in items) {
        coroutineScope.coroutineContext.ensureActive()
        curArranger.associateSorter(item.lookupElement, item.sorter as CompletionSorterImpl)
        lookup.addItem(item.lookupElement, item.prefixMatcher)
      }
    }
    curArranger.setLastLookupPrefix(lookup.additionalPrefix)
  }

  /** Returns true if the completion popup was shown to the user */
  @RequiresEdt
  fun tryInsertOrShowPopup(): Boolean {
    lookup.isCalculating = false
    lookup.refreshUi(true, false)
    val items = lookup.items
    if (items.isEmpty()) {
      return false
    }

    if (!isAutopopupCompletion && items.size == 1) {
      // Auto-insert a single suggestion if completion is called using action
      lookup.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR, items.single())
      return false
    }

    if (!lookup.isShown) {
      val shown = lookup.showLookup()
      if (!shown) return false
      lookup.refreshUi(true, true)
      lookup.ensureSelectionVisible(true)
    }
    return true
  }

  override fun prefixUpdated() {
    val startOffset = lookup.lookupStart
    val cursorOffset = context.outputModel.cursorOffset.toRelative(context.outputModel)
    if (cursorOffset > startOffset && restartOnPrefixChange) {
      scheduleRestart()
    }
    closeLookupIfMeaningless()
  }

  /**
   * Similar to [com.intellij.codeInsight.completion.CompletionProgressIndicator.hideAutopopupIfMeaningless]
   * but closes the lookup even if was called manually.
   */
  @RequiresEdt
  private fun closeLookupIfMeaningless() {
    if (lookup.isLookupDisposed || lookup.isCalculating || restartPending) {
      return
    }

    lookup.refreshUi(true, true)
    if (isPopupMeaningless()) {
      cancel()
    }
  }

  @RequiresEdt
  fun isPopupMeaningless(): Boolean {
    val items = lookup.items
    return items.isEmpty() || items.all {
      isAlreadyTyped(it)
    }
  }

  /** Similar to [com.intellij.codeInsight.completion.CompletionProgressIndicator.isAlreadyInTheEditor] */
  private fun isAlreadyTyped(element: LookupElement): Boolean {
    val model = context.outputModel
    val startOffset = model.cursorOffset - lookup.itemPattern(element).length.toLong()
    return startOffset >= model.startOffset
           && model.getText(startOffset, model.endOffset).startsWith(element.lookupString)
  }

  override fun itemSelected(item: LookupElement?, completionChar: Char) {
    cancel()
  }

  override fun scheduleRestart() {
    if (restartPending) {
      return
    }
    restartPending = true
    cancel()

    TerminalCommandCompletionService.getInstance(project).invokeCompletion(
      context.terminalView,
      context.editor,
      context.outputModel,
      context.shellIntegration,
      context.isAutoPopup
    )
  }

  override fun addWatchedPrefix(startOffset: Int, restartCondition: ElementPattern<String?>) {
    // Now this method is called only to restart on any prefix change
    restartOnPrefixChange = true
  }

  @RequiresEdt
  fun cancel() {
    coroutineScope.cancel()
    lookup.isCalculating = false
    if (!restartPending) {
      lookup.hideLookup(false)
    }
  }

  override fun addAdvertisement(message: @NlsContexts.PopupAdvertisement String, icon: Icon?) {
    // do nothing - we have our own advertisement in the terminal completion popup.
  }

  override fun setParameters(parameters: CompletionParameters) {
    // Not expected to be called on our implementation of CompletionProcessEx
    throw NotImplementedError()
  }

  override fun getOffsetMap(): OffsetMap {
    // Not expected to be called on our implementation of CompletionProcessEx
    throw NotImplementedError()
  }

  override fun getHostOffsets(): OffsetsInFile {
    // Not expected to be called on our implementation of CompletionProcessEx
    throw NotImplementedError()
  }

  override fun registerChildDisposable(child: Supplier<out Disposable?>) {
    // Not expected to be called on our implementation of CompletionProcessEx
    throw NotImplementedError()
  }
}

internal data class TerminalCommandCompletionContext(
  val project: Project,
  val terminalView: TerminalView,
  val editor: Editor,
  val outputModel: TerminalOutputModel,
  val shellIntegration: TerminalShellIntegration,
  val commandStartOffset: TerminalOffset,
  val initialCursorOffset: TerminalOffset,
  /** Full command text at the moment of completion request. May include trailing new lines and spaces. */
  val commandText: String,
  val isAutoPopup: Boolean,
)