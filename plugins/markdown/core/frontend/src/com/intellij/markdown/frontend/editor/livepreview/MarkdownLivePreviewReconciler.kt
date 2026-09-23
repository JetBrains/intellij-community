// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.isLivePreviewEnabled
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Applies element presentations to one editor while preserving its carets, selections, and viewport.
 * Revealing source removes its owned folds and their decorations.
 */
@ApiStatus.Internal
class MarkdownLivePreviewReconciler private constructor(
  private val project: Project,
  private val editor: EditorEx,
) : Disposable {

  private var presentation: MarkdownLivePreviewPresentation? = null

  /**
   * The regions we own, keyed by the range each conceals. Regions move with the text, so [reconcileNow]
   * re-keys them from the regions themselves before it trusts the keys.
   */
  private val ownedRegions = LinkedHashMap<TextRange, OwnedFold>()
  private val presentationFactory = MarkdownLivePreviewPresentationFactory(project, editor)

  /** The elements already revealed, so [revealNow] can act on the difference alone. */
  private var revealedElements = emptySet<MarkdownLivePreviewElementPresentation>()

  /** Set while we are mutating folds ourselves, so our own listeners do not reenter. */
  private var updating = false
  private var reconcileScheduled = false
  @Volatile private var disposed = false

  init {
    Disposer.register(this, presentationFactory)
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) = onCaretChanged()
      override fun caretAdded(event: CaretEvent) = onCaretChanged()
      override fun caretRemoved(event: CaretEvent) = onCaretChanged()
    }, this)
    editor.selectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(event: SelectionEvent) = onCaretChanged()
    }, this)
    editor.document.addDocumentListener(object : BulkAwareDocumentListener {
      override fun documentChangedNonBulk(event: DocumentEvent) = scheduleReconcile()
      override fun bulkUpdateFinished(document: Document) = scheduleReconcile()
    }, this)
    editor.foldingModel.addListener(object : FoldingListener {
      override fun onFoldProcessingEnd() = scheduleReconcile()
    }, this)
    editor.addPropertyChangeListener({ scheduleReconcile() }, this)
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      scheduleReconcile()
    })
    editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() = scheduleReconcile()
      override fun recalculationEnds() = scheduleReconcile()
    })
  }

  /**
   * Hands over the specs computed for one state of the document and brings the editor in line with them.
   * Called on the EDT once the highlighting pass has finished computing.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun publishSpecs(specSet: MarkdownLivePreviewSpecSet?) {
    if (editor.isDisposed || disposed) return
    if (specSet != null && !specSet.documentVersion.matches(editor.document, project)) return
    if (specSet != null && presentation?.documentVersion?.matchesDocument(specSet.documentVersion) != true) {
      presentationFactory.documentChanged()
    }
    presentation = specSet?.let(presentationFactory::create)
    revealedElements = emptySet()
    reconcileNow()
  }

  @TestOnly
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun hasCurrentSpecs(): Boolean = currentPresentation() != null

  /**
   * Brings the fold regions fully in line with the current specs and caret positions, adding and removing
   * only what actually differs. Does nothing while the specs describe an older state of the document; the
   * next highlighting pass supersedes them.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun reconcileNow() {
    if (updating || editor.isDisposed || disposed || editor.document.isInBulkUpdate) return
    if (!editor.isLivePreviewEnabled() || presentation == null) {
      removeAllOwned()
      return
    }
    val presentation = currentPresentation() ?: return
    val revealed = findRevealedElements(presentation)
    runEditorUpdate {
      reconcileFoldRegions(desiredRegions(presentation, revealed))
      presentationFactory.reconcile(presentation)
    }
    revealedElements = revealed
  }

  override fun dispose() {
    disposed = true
    presentation = null
    val cleanup = Runnable {
      if (!editor.isDisposed) runEditorUpdate { removeOwned(ownedRegions.keys.toList()) }
      ownedRegions.clear()
    }
    val application = ApplicationManager.getApplication()
    if (ApplicationManager.getApplication().isDispatchThread) cleanup.run() else application.invokeLater(cleanup, ModalityState.any())
  }

  private fun currentPresentation(): MarkdownLivePreviewPresentation? {
    return presentation?.takeIf { it.documentVersion.matches(editor.document, project) }
  }

  private fun desiredRegions(
    presentation: MarkdownLivePreviewPresentation,
    revealed: Set<MarkdownLivePreviewElementPresentation>,
  ): Map<TextRange, MarkdownLivePreviewFold> {
    val regions = LinkedHashMap<TextRange, MarkdownLivePreviewFold>()
    for (element in presentation.elements) {
      if (element in revealed) continue
      element.folds.forEach { regions[it.range] = it }
    }
    return regions
  }

  private fun reconcileFoldRegions(
    desired: Map<TextRange, MarkdownLivePreviewFold>,
  ) {
    val existing = LinkedHashMap<TextRange, OwnedFold>()
    val obsolete = ArrayList<OwnedFold>()
    for (owned in ownedRegions.values) {
      if (!owned.region.isValid) obsolete += owned
      else existing.put(owned.region.currentRange(), owned)?.let(obsolete::add)
    }
    val kept = LinkedHashMap<TextRange, OwnedFold>()
    val missing = LinkedHashMap<TextRange, MarkdownLivePreviewFold>()
    for ((range, wanted) in desired) {
      val owned = existing.remove(range)
      if (owned != null && wanted.isSame(owned.region)) {
        kept[range] = owned
      }
      else {
        if (owned != null) obsolete += owned
        missing[range] = wanted
      }
    }
    obsolete += existing.values
    val decorationChanges = LinkedHashSet<TextRange>()
    for (range in desired.keys) {
      val owned = kept[range] ?: continue
      if (owned.decorationSource !== desired.getValue(range).decoration) decorationChanges += range
    }
    if (obsolete.isEmpty() && missing.isEmpty() && decorationChanges.isEmpty()) return
    ownedRegions.clear()
    ownedRegions.putAll(kept)
    updateFoldRegions(obsolete, missing)
    val rangesToReconcile = missing.keys + decorationChanges
    val failed = reconcileDecorations(desired, rangesToReconcile).mapNotNull { ownedRegions.remove(it) }
    updateFoldRegions(failed)
  }

  private fun reconcileDecorations(
    desired: Map<TextRange, MarkdownLivePreviewFold>,
    ranges: Collection<TextRange>,
  ): List<TextRange> {
    val failed = ArrayList<TextRange>()
    for ((range, owned) in ownedRegions) {
      if (range !in ranges) continue
      val wanted = desired.getValue(range).decoration
      if (wanted == null) {
        owned.disposeMountedDecoration()
        continue
      }
      if (owned.decoration?.update(wanted) == true) {
        owned.decorationSource = wanted
        continue
      }
      owned.disposeMountedDecoration()
      val decoration = wanted.create(owned.region)
      if (decoration == null) failed += range
      else {
        owned.decoration = decoration
        owned.decorationSource = wanted
      }
    }
    return failed
  }

  private fun onCaretChanged() {
    revealNow()
    scheduleReconcile()
  }

  /** Removes the regions of every element a caret or selection has just reached. */
  private fun revealNow() {
    if (updating || editor.isDisposed || disposed) return
    val document = editor.document
    // While a bulk change runs, the fold tree is not maintained. During event handling the folding model
    // may still be catching up, since it is a document listener itself.
    if (document.isInBulkUpdate || document.isInEventsHandling) return
    if (!editor.isLivePreviewEnabled()) return
    val presentation = currentPresentation() ?: return
    val revealed = findRevealedElements(presentation)
    val newlyRevealed = revealed - revealedElements
    if (newlyRevealed.isEmpty()) return
    runEditorUpdate {
      removeOwned(newlyRevealed.flatMap { element -> element.folds.map { it.range } })
      revealedElements = revealedElements + newlyRevealed
      presentationFactory.reconcile(presentation)
    }
  }

  /** Removes the owned folds and decorations at the current document [ranges]. */
  private fun removeOwned(ranges: Collection<TextRange>) {
    val regions = ranges.mapNotNull { ownedRegions.remove(it) }
    if (regions.isNotEmpty()) {
      updateFoldRegions(regions)
    }
  }

  private fun removeAllOwned() {
    runEditorUpdate {
      removeOwned(ownedRegions.keys.toList())
      presentationFactory.reconcile(null)
    }
    revealedElements = emptySet()
  }

  private fun scheduleReconcile() {
    if (updating || reconcileScheduled || editor.isDisposed || disposed) return
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
   * Finds the elements a caret or selection touches, and which therefore show their markup.
   *
   * Both ends count as touching, so a caret resting immediately after `**bold**` already reveals it. That
   * is what keeps a concealing region from ever sitting under a caret, which in turn keeps the platform
   * from pushing carets around or dropping selections that end on a region boundary.
   *
   * Every caret is considered, which covers multiple carets and column selection alike: a column selection
   * is one caret per visual line, each with its own selection.
   */
  private fun findRevealedElements(presentation: MarkdownLivePreviewPresentation): Set<MarkdownLivePreviewElementPresentation> {
    val revealed = HashSet<MarkdownLivePreviewElementPresentation>()
    for (caret in editor.caretModel.allCarets) {
      revealed.addAll(intersecting(presentation, caret.selectionStart, caret.selectionEnd))
    }
    return revealed
  }

  /** Returns the elements that intersect the closed interval [start], [end]. */
  private fun intersecting(
    presentation: MarkdownLivePreviewPresentation,
    start: Int,
    end: Int,
  ): Set<MarkdownLivePreviewElementPresentation> {
    val elements = presentation.elements
    val into = mutableSetOf<MarkdownLivePreviewElementPresentation>()
    var index = firstElementAfter(elements, end)
    val lowestStart = start - presentation.maxElementLength
    while (index-- > 0) {
      val element = elements[index]
      if (element.spec.range.startOffset < lowestStart) break
      if (element.spec.range.endOffset >= start) into.add(element)
    }
    return into
  }

  private fun firstElementAfter(elements: List<MarkdownLivePreviewElementPresentation>, offset: Int): Int {
    var low = 0
    var high = elements.size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (elements[mid].spec.range.startOffset > offset) high = mid else low = mid + 1
    }
    return low
  }

  /** Completes removal before creating replacement folds. Decorations are mounted after the folding batches. */
  private fun updateFoldRegions(
    removed: Collection<OwnedFold>,
    added: Map<TextRange, MarkdownLivePreviewFold> = emptyMap(),
  ) {
    removed.forEach(Disposer::dispose)
    if (removed.isNotEmpty()) {
      editor.foldingModel.runBatchFoldingOperation({
        for (owned in removed) {
          if (owned.region.isValid) editor.foldingModel.removeFoldRegion(owned.region)
        }
      }, false, false)
    }
    if (added.isNotEmpty()) {
      editor.foldingModel.runBatchFoldingOperation({
        for ((range, wanted) in added) {
          val region = wanted.create(editor) ?: continue
          val owned = OwnedFold(region)
          Disposer.register(this, owned)
          ownedRegions[range] = owned
        }
      }, false, false)
    }
  }

  /** Preserves each caret and the viewport across fold changes, decoration updates, and any failed decoration cleanup. */
  private fun runEditorUpdate(body: () -> Unit) {
    val snapshot = editor.caretModel.allCarets.map { CaretSnapshot(it) }
    updating = true
    try {
      EditorScrollingPositionKeeper.perform(editor, false) {
        body()
        snapshot.forEach { it.restore() }
      }
    }
    finally {
      updating = false
    }
  }

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

  private class OwnedFold(val region: FoldRegion) : Disposable {
    var decoration: MarkdownLivePreviewMountedDecoration? = null
    var decorationSource: MarkdownLivePreviewFoldDecoration? = null

    fun disposeMountedDecoration() {
      val previous = decoration ?: return
      decoration = null
      decorationSource = null
      Disposer.dispose(previous)
    }

    override fun dispose() = disposeMountedDecoration()
  }

  companion object {
    private val KEY = Key.create<MarkdownLivePreviewReconciler>("markdown.live.preview.reconciler")

    /** The reconciler for [editor], attaching one on first use. Null in cases a reconciler can't be created. */
    @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
    fun getOrCreate(editor: Editor): MarkdownLivePreviewReconciler? {
      if (editor !is EditorEx || editor.isDisposed) return null
      val project = editor.project ?: return null
      editor.getUserData(KEY)?.let { return it }
      return MarkdownLivePreviewReconciler(project, editor).also {
        editor.putUserData(KEY, it)
        EditorUtil.disposeWithEditor(editor, it)
      }
    }

    fun getExisting(editor: Editor): MarkdownLivePreviewReconciler? = editor.getUserData(KEY)
  }
}

private fun FoldRegion.currentRange(): TextRange = TextRange(startOffset, endOffset)
