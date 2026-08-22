// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownConcealSpecSet
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Marks the fold regions live preview created, so it only ever removes its own and can recognize one that
 * came back from somewhere else - the clipboard carries collapsed regions along with the text it copies.
 */
@VisibleForTesting
val MARKDOWN_LIVE_PREVIEW_REGION: Key<Boolean> = Key.create("markdown.live.preview.region")

private val AllowedEditorKinds = setOf(EditorKind.MAIN_EDITOR, EditorKind.UNTYPED)

/**
 * Keeps one editor's concealing fold regions in step with the caret.
 *
 * Concealment is an empty, never-expanding fold region. The platform never expands one of those, so
 * revealing markup means removing the region, and this class owns the regions outright.
 */
@ApiStatus.Internal
class MarkdownLivePreviewReconciler private constructor(private val editor: EditorEx): Disposable {

  private var specSet: MarkdownConcealSpecSet? = null

  /**
   * The regions we own, keyed by the concealed range each was created for. The keys go stale as soon as the
   * document changes - regions move with the text - so [reconcileNow] re-keys them from the regions
   * themselves, and every path that trusts the keys first checks the spec set against the document stamp.
   */
  private val ownedRegions = HashMap<TextRange, FoldRegion>()

  /** Indices of the elements we have already revealed, so [revealNow] can act on the difference alone. */
  private var revealedElements = emptySet<Int>()

  /** Set while we are mutating folds ourselves, so our own listeners do not reenter. */
  private var updating = false

  private var reconcileScheduled = false
  private var disposed = false

  init {
    installListeners()
  }

  private fun installListeners() {
    editor.caretModel.addCaretListener(object: CaretListener {
      override fun caretPositionChanged(event: CaretEvent) = onCaretChanged()
      override fun caretAdded(event: CaretEvent) = onCaretChanged()
      override fun caretRemoved(event: CaretEvent) = onCaretChanged()
    }, this)
    editor.selectionModel.addSelectionListener(object: SelectionListener {
      override fun selectionChanged(event: SelectionEvent) = onCaretChanged()
    }, this)
  }

  /**
   * Hands over the specs computed for one state of the document and brings the editor in line with them.
   * Called on the EDT once the highlighting pass has finished computing.
   */
  @RequiresEdt
  fun publishSpecs(specSet: MarkdownConcealSpecSet) {
    ThreadingAssertions.assertEventDispatchThread()
    this.specSet = specSet
    reconcileNow()
  }

  /**
   * Brings the fold regions fully in line with the current specs and caret positions, adding and removing
   * only what actually differs. Does nothing while the specs describe an older state of the document; the
   * next highlighting pass supersedes them.
   */
  @RequiresEdt
  fun reconcileNow() {
    ThreadingAssertions.assertEventDispatchThread()
    if (disposed || updating || editor.isDisposed || editor.document.isInBulkUpdate) return
    if (!isLivePreviewEnabled(editor)) {
      removeAllOwned()
      return
    }
    val specSet = this.specSet ?: return
    if (specSet.documentStamp != editor.document.modificationStamp) return

    val revealed = revealedElementIndices(specSet)
    val desired = HashSet<TextRange>()
    specSet.elements.forEachIndexed { index, element ->
      if (index !in revealed) {
        desired.addAll(element.conceals)
      }
    }

    // Re-key by where the regions actually are now, which is how a region survives an edit that only
    // shifted it, and how a region the clipboard brought back gets picked up instead of duplicated.
    val existing = HashMap<TextRange, FoldRegion>()
    for (region in ownedRegions.values) {
      if (region.isValid) existing[region.currentRange()] = region
    }

    val toRemove = existing.entries.filter { it.key !in desired }.map { it.value }
    val toAdd = desired.filterNot { it in existing }
    val next = HashMap(existing)
    if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
      toRemove.forEach { next.remove(it.currentRange()) }
      runFoldBatch {
        toRemove.forEach { editor.foldingModel.removeFoldRegion(it) }
        for (range in toAdd) {
          concealRange(range)?.let { next[range] = it }
        }
      }
    }
    ownedRegions.clear()
    ownedRegions.putAll(next)
    revealedElements = revealed
  }

  // Deliberately not @RequiresEdt: Disposer may call this from any thread, and it only drops state.
  override fun dispose() {
    disposed = true
    ownedRegions.clear()
    revealedElements = emptySet()
    specSet = null
  }

  private fun onCaretChanged() {
    revealNow()
    scheduleReconcile()
  }

  /** Removes the regions of every element a caret or selection has just reached.  */
  private fun revealNow() {
    if (disposed || updating || editor.isDisposed) return
    val document = editor.document
    // While a bulk change runs the fold tree is not maintained at all, so leave it alone entirely.
    if (document.isInBulkUpdate) return
    // The folding model is a document listener in its own right, so its tree may still be catching up.
    if (document.isInEventsHandling) return
    val specSet = this.specSet ?: return
    if (specSet.documentStamp != document.modificationStamp) return
    if (!isLivePreviewEnabled(editor)) return

    val revealed = revealedElementIndices(specSet)
    val newlyRevealed = revealed - revealedElements
    if (newlyRevealed.isEmpty()) return

    val toRemove = mutableListOf<Pair<TextRange, FoldRegion>>()
    for (index in newlyRevealed) {
      for (range in specSet.elements[index].conceals) {
        val region = ownedRegions[range] ?: continue
        if (region.isValid) toRemove.add(range to region)
      }
    }
    if (toRemove.isNotEmpty()) {
      runFoldBatch { toRemove.forEach { editor.foldingModel.removeFoldRegion(it.second) } }
      toRemove.forEach { ownedRegions.remove(it.first) }
    }
    revealedElements = revealedElements + newlyRevealed
  }

  private fun scheduleReconcile() {
    if (disposed || reconcileScheduled) return
    reconcileScheduled = true
    ApplicationManager.getApplication().invokeLater(
      {
        reconcileScheduled = false
        reconcileNow()
      },
      ModalityState.any(),
    )
  }

  /**
   * Indices of the elements a caret or selection touches, and which therefore show their markup.
   *
   * Both ends count as touching, so a caret resting immediately after `**bold**` already reveals it. That
   * is what keeps a concealing region from ever sitting under a caret, which in turn keeps the platform
   * from pushing carets around or dropping selections that end on a region boundary.
   *
   * Every caret is considered, which covers multiple carets and column selection alike: a column selection
   * is one caret per visual line, each with its own selection.
   */
  private fun revealedElementIndices(specSet: MarkdownConcealSpecSet): Set<Int> {
    val revealed = HashSet<Int>()
    for (caret in editor.caretModel.allCarets) {
      specSet.intersecting(caret.selectionStart, caret.selectionEnd, revealed)
    }
    return revealed
  }

  private fun concealRange(range: TextRange): FoldRegion? {
    val foldingModel = editor.foldingModel
    val existing = foldingModel.getFoldRegion(range.startOffset, range.endOffset)
    if (existing != null) {
      // A region is already here, most likely restored from the clipboard together with pasted text. The
      // folding model refuses a second region over the same range, so adopt this one instead.
      if (!existing.isValid || existing is CustomFoldRegion || !existing.shouldNeverExpand() || existing.placeholderText.isNotEmpty()) {
        return null
      }
      existing.putUserData(MARKDOWN_LIVE_PREVIEW_REGION, true)
      return existing
    }
    // Can come back null for reasons beyond a caret being in the way - folding switched off, or a boundary
    // that would split a character pair - so never assume the region exists.
    val region = foldingModel.createFoldRegion(range.startOffset, range.endOffset, "", null, true) ?: return null
    region.putUserData(MARKDOWN_LIVE_PREVIEW_REGION, true)
    return region
  }

  private fun removeAllOwned() {
    val regions = ownedRegions.values.filter { it.isValid }
    ownedRegions.clear()
    revealedElements = emptySet()
    if (regions.isEmpty()) return
    runFoldBatch { regions.forEach { editor.foldingModel.removeFoldRegion(it) } }
  }

  /**
   * Runs one batch of fold changes and puts the carets back where they were.
   *
   * Every batch ends in `onFoldProcessingEnd`, which always takes the caret-moving branch - the flag that would stop
   * it is hardcoded on - and a caret keeps a saved position for as long as the document is untouched. So a position left behind by some
   * other folding client, such as collapsing a code fence with the caret inside it, would be applied to our batch and
   * jump the caret back there. Restoring is safe because no caret is ever inside a region of ours.
   */
  private fun runFoldBatch(body: () -> Unit) {
    val snapshot = editor.caretModel.allCarets.map { CaretSnapshot(it) }
    updating = true
    try {
      // allowMovingCaret = false, so a stale spec is refused rather than allowed to drag the caret;
      // keepRelativeCaretPosition = false because zero-width regions never shift what is on screen.
      editor.foldingModel.runBatchFoldingOperation(body, false, false)
      snapshot.forEach { it.restore() }
    }
    finally {
      updating = false
    }
  }

  /**
   * Whether [editor] should hide Markdown markup right now.
   *
   * Evaluated on every reconciliation rather than watched through listeners: the reconciler already runs on
   * caret and selection changes, and the find toolbar in particular gives no reliable signal when it closes.
   */
  private fun isLivePreviewEnabled(editor: Editor): Boolean {
    val project = editor.project ?: return false
    // Diff, preview and console editors have their layout managed for them, and concealing markup would fight that.
    // Both EditorKind.MAIN_EDITOR and EditorKind.UNTYPED are allowed because there are plenty of ordinary editors that use the latter kind.
    return MarkdownSettings.getInstance(project).enableLivePreview && editor.editorKind in AllowedEditorKinds
  }

  private fun FoldRegion.currentRange(): TextRange = TextRange(startOffset, endOffset)

  private class CaretSnapshot(private val caret: Caret) {
    private val offset = caret.offset
    private val selectionStart = caret.selectionStart
    private val selectionEnd = caret.selectionEnd
    private val hadSelection = caret.hasSelection()

    fun restore() {
      if (!caret.isValid) return
      if (caret.offset != offset) {
        caret.moveToOffset(offset)
      }
      if (hadSelection && (caret.selectionStart != selectionStart || caret.selectionEnd != selectionEnd)) {
        caret.setSelection(selectionStart, selectionEnd)
      }
    }
  }

  companion object {
    private val KEY = Key.create<MarkdownLivePreviewReconciler>("markdown.live.preview.reconciler")

    /** The reconciler for [editor], attaching one on first use. Null for editors that cannot host folds. */
    @RequiresEdt
    fun getOrCreate(editor: Editor): MarkdownLivePreviewReconciler? {
      ThreadingAssertions.assertEventDispatchThread()
      if (editor !is EditorEx || editor.isDisposed) return null
      editor.getUserData(KEY)?.let { return it }
      val reconciler = MarkdownLivePreviewReconciler(editor)
      editor.putUserData(KEY, reconciler)
      Disposer.register(reconciler) { editor.putUserData(KEY, null) }
      EditorUtil.disposeWithEditor(editor, reconciler)
      return reconciler
    }

    fun getExisting(editor: Editor): MarkdownLivePreviewReconciler? = editor.getUserData(KEY)
  }
}
