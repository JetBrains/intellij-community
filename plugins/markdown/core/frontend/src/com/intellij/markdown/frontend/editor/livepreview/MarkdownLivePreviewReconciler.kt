// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.FoldingKeys
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.editor.livepreview.toTextRange
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics
import java.awt.Graphics2D

private val AllowedEditorKinds = setOf(EditorKind.MAIN_EDITOR, EditorKind.UNTYPED)

/**
 * Keeps one editor's concealing fold regions in step with the caret.
 *
 * Concealment is a never-expanding fold region. Revealing markup means removing the region, and this class
 * owns the regions outright.
 */
@ApiStatus.Internal
class MarkdownLivePreviewReconciler private constructor(
  private val project: Project,
  private val editor: EditorEx,
) : Disposable {

  private var specSet: MarkdownLivePreviewSpecSet? = null

  /**
   * The regions we own, keyed by the range each conceals. Regions move with the text, so [reconcileNow]
   * re-keys them from the regions themselves before it trusts the keys.
   */
  private val ownedRegions = LinkedHashMap<TextRange, FoldRegion>()
  private val ownedHorizontalRules = LinkedHashMap<TextRange, RangeHighlighter>()
  private val imageRenderer = MarkdownLivePreviewImageRenderer(project, editor)

  /** Indices of the elements we have already revealed, so [revealNow] can act on the difference alone. */
  private var revealedElements = emptySet<Int>()

  /** Set while we are mutating folds ourselves, so our own listeners do not reenter. */
  private var updating = false
  private var reconcileScheduled = false

  init {
    Disposer.register(this, imageRenderer)
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
    project.messageBus.connect(this).subscribe(MarkdownSettings.ChangeListener.TOPIC, object : MarkdownSettings.ChangeListener {
      override fun settingsChanged(settings: MarkdownSettings) = scheduleReconcile()
    })
  }

  /**
   * Hands over the specs computed for one state of the document and brings the editor in line with them.
   * Called on the EDT once the highlighting pass has finished computing.
   */
  @RequiresEdt
  fun publishSpecs(specSet: MarkdownLivePreviewSpecSet) {
    if (this.specSet?.documentVersion?.matchesDocument(specSet.documentVersion) != true) {
      imageRenderer.resetRequestedImages()
    }
    this.specSet = specSet
    reconcileNow()
  }

  @TestOnly
  @RequiresEdt
  fun hasCurrentSpecs(): Boolean = currentSpecSet() != null

  /**
   * Brings the fold regions fully in line with the current specs and caret positions, adding and removing
   * only what actually differs. Does nothing while the specs describe an older state of the document; the
   * next highlighting pass supersedes them.
   */
  @RequiresEdt
  fun reconcileNow() {
    if (updating || editor.isDisposed || editor.document.isInBulkUpdate) return
    if (!isLivePreviewEnabled()) {
      removeAllOwned()
      return
    }
    val specSet = currentSpecSet() ?: return
    val revealed = revealedElementIndices(specSet)
    val desired = desiredRegions(specSet, revealed)
    reconcileFoldRegions(desired)
    reconcileHorizontalRules(desired)
    revealedElements = revealed
  }

  /**
   * Deletes the line break between an image and the caret instead of a character of the hidden image source.
   * Returns false when the caret is not next to an image, so the default handler runs.
   */
  @RequiresEdt
  fun handleBackspace(): Boolean {
    if (editor.isDisposed || editor.selectionModel.hasSelection()) return false
    val document = editor.document
    val text = document.charsSequence
    val caretOffset = editor.caretModel.offset
    val lineBreak = when {
      caretOffset > 0 && text[caretOffset - 1] == '\n' -> caretOffset - 1
      caretOffset < text.length && text[caretOffset] == '\n' -> caretOffset
      else -> return false
    }
    val region = ownedRegions.values.firstOrNull { it is CustomFoldRegion && it.isValid && it.endOffset == lineBreak } ?: return false
    removeOwned(listOf(region.currentRange()))
    runWriteAction { document.deleteString(lineBreak, lineBreak + 1) }
    return true
  }

  // Not @RequiresEdt: Disposer may call this from any thread, and it only drops state.
  override fun dispose() {
    specSet = null
  }

  private fun currentSpecSet(): MarkdownLivePreviewSpecSet? {
    return specSet?.takeIf { it.documentVersion.matches(editor.document, project) }
  }

  private fun desiredRegions(specSet: MarkdownLivePreviewSpecSet, revealed: Set<Int>): Map<TextRange, OwnedRegion> {
    val regions = LinkedHashMap<TextRange, OwnedRegion>()
    specSet.elements.forEachIndexed { index, spec ->
      if (index in revealed) return@forEachIndexed
      when (spec) {
        is MarkdownLivePreviewSpec.Conceal -> spec.conceals.forEach { regions[it.toTextRange()] = OwnedRegion.Text("") }
        is MarkdownLivePreviewSpec.HorizontalRule -> regions[spec.range.toTextRange()] = OwnedRegion.HorizontalRule
        is MarkdownLivePreviewSpec.Bullet -> regions[spec.concealRange.toTextRange()] = OwnedRegion.Text(spec.placeholderText)
        is MarkdownLivePreviewSpec.Image -> {
          if (spec.source == null) imageRenderer.requestImage(spec.destination)
          else regions[spec.range.toTextRange()] = OwnedRegion.Image(spec.destination, specSet.documentVersion.elementsHash)
        }
      }
    }
    return regions
  }

  private fun reconcileFoldRegions(desired: Map<TextRange, OwnedRegion>) {
    // Re-key by where the regions are now. A shifted region then survives its edit, and a region the
    // clipboard brought back is adopted instead of duplicated.
    val existing = ownedRegions.values.filter { it.isValid }.associateBy { it.currentRange() }
    val kept = LinkedHashMap<TextRange, FoldRegion>()
    val missing = LinkedHashMap<TextRange, OwnedRegion>()
    for ((range, wanted) in desired) {
      val region = existing[range]
      if (region == null || !wanted.matches(region)) {
        missing[range] = wanted
        continue
      }
      kept[range] = region
      if (wanted is OwnedRegion.Image) imageRenderer.updateRegion(region, wanted.elementsHash)
    }
    val obsolete = existing.filterKeys { it !in kept }.values
    ownedRegions.clear()
    ownedRegions.putAll(kept)
    if (obsolete.isEmpty() && missing.isEmpty()) return
    runFoldBatch {
      obsolete.forEach(editor.foldingModel::removeFoldRegion)
      for ((range, wanted) in missing) {
        ownedRegions[range] = createRegion(range, wanted) ?: continue
      }
    }
  }

  private fun reconcileHorizontalRules(desired: Map<TextRange, OwnedRegion>) {
    val wanted = desired.filterValues { it == OwnedRegion.HorizontalRule }.keys
      .filterTo(LinkedHashSet()) { ownedRegions[it]?.isValid == true }
    val existing = ownedHorizontalRules.values.filter { it.isValid }.associateBy { it.textRange }
    existing.filterKeys { it !in wanted }.values.forEach { it.dispose() }
    ownedHorizontalRules.clear()
    for (range in wanted) {
      ownedHorizontalRules[range] = existing[range] ?: createHorizontalRule(range)
    }
  }

  private fun onCaretChanged() {
    revealNow()
    scheduleReconcile()
  }

  /** Removes the regions of every element a caret or selection has just reached. */
  private fun revealNow() {
    if (updating || editor.isDisposed) return
    val document = editor.document
    // While a bulk change runs, the fold tree is not maintained. During event handling the folding model
    // may still be catching up, since it is a document listener itself.
    if (document.isInBulkUpdate || document.isInEventsHandling) return
    if (!isLivePreviewEnabled()) return
    val specSet = currentSpecSet() ?: return
    val revealed = revealedElementIndices(specSet)
    val newlyRevealed = revealed - revealedElements
    if (newlyRevealed.isEmpty()) return
    removeOwned(newlyRevealed.flatMap { specSet.elements[it].concealedRanges() })
    revealedElements = revealedElements + newlyRevealed
  }

  /** Removes the owned regions and rules at [ranges], which must be current document ranges. */
  private fun removeOwned(ranges: Collection<TextRange>) {
    val regions = ranges.mapNotNull { ownedRegions.remove(it) }.filter { it.isValid }
    ranges.mapNotNull { ownedHorizontalRules.remove(it) }.filter { it.isValid }.forEach { it.dispose() }
    if (regions.isNotEmpty()) runFoldBatch { regions.forEach(editor.foldingModel::removeFoldRegion) }
  }

  private fun removeAllOwned() {
    removeOwned(ownedRegions.keys.toList() + ownedHorizontalRules.keys)
    revealedElements = emptySet()
  }

  private fun scheduleReconcile() {
    if (reconcileScheduled || editor.isDisposed) return
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
  private fun revealedElementIndices(specSet: MarkdownLivePreviewSpecSet): Set<Int> {
    val revealed = HashSet<Int>()
    for (caret in editor.caretModel.allCarets) {
      specSet.intersecting(caret.selectionStart, caret.selectionEnd, revealed)
    }
    return revealed
  }

  private fun createRegion(range: TextRange, wanted: OwnedRegion): FoldRegion? {
    return when (wanted) {
      is OwnedRegion.Text -> createTextRegion(range, wanted.placeholderText)
      OwnedRegion.HorizontalRule -> createTextRegion(range, "")
      is OwnedRegion.Image -> imageRenderer.createRegion(range, wanted.destination, wanted.elementsHash)
    }
  }

  private fun createTextRegion(range: TextRange, placeholderText: String): FoldRegion? {
    val foldingModel = editor.foldingModel
    val existing = foldingModel.getFoldRegion(range.startOffset, range.endOffset)
    val region = when {
      // The folding model can refuse a region: folding may be off, or a boundary may split a character pair.
      existing == null -> foldingModel.createFoldRegion(range.startOffset, range.endOffset, placeholderText, null, true)
      // A region is already here, most likely restored from the clipboard together with pasted text. The
      // folding model refuses a second region over the same range, so adopt this one instead.
      existing.isValid && existing !is CustomFoldRegion && existing.shouldNeverExpand() && existing.placeholderText == placeholderText -> existing
      else -> null
    } ?: return null
    if (placeholderText.isNotEmpty()) region.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true)
    return region
  }

  private fun createHorizontalRule(range: TextRange): RangeHighlighter {
    val highlighter = editor.markupModel.addRangeHighlighter(
      MarkdownHighlighterColors.HRULE, range.startOffset, range.endOffset,
      HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
    )
    highlighter.customRenderer = MarkdownHorizontalRuleRenderer
    return highlighter
  }

  /**
   * Runs the fold changes for one reconciliation while preserving each caret and the viewport anchor.
   *
   * Each reconciliation that changes fold regions must call this method exactly once. The call must contain
   * all fold-region removals and additions for that reconciliation.
   */
  private fun runFoldBatch(body: () -> Unit) {
    val snapshot = editor.caretModel.allCarets.map { CaretSnapshot(it) }
    updating = true
    try {
      MarkdownLivePreviewPositionKeeper.perform(editor) {
        editor.foldingModel.runBatchFoldingOperation(body, false, false)
        snapshot.forEach { it.restore() }
      }
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
  private fun isLivePreviewEnabled(): Boolean {
    // Diff, preview and console editors have their layout managed for them, and concealing markup would fight that.
    // Both EditorKind.MAIN_EDITOR and EditorKind.UNTYPED are allowed because there are plenty of ordinary editors that use the latter kind.
    return MarkdownSettings.getInstance(project).enableLivePreview && editor.editorKind in AllowedEditorKinds
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

  companion object {
    private val KEY = Key.create<MarkdownLivePreviewReconciler>("markdown.live.preview.reconciler")

    /** The reconciler for [editor], attaching one on first use. Null in cases a reconciler can't be created. */
    @RequiresEdt
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

/** The ranges an element hides, and which its owned regions are keyed by. */
private fun MarkdownLivePreviewSpec.concealedRanges(): List<TextRange> {
  return when (this) {
    is MarkdownLivePreviewSpec.Conceal -> conceals.map { it.toTextRange() }
    is MarkdownLivePreviewSpec.Bullet -> listOf(concealRange.toTextRange())
    is MarkdownLivePreviewSpec.HorizontalRule, is MarkdownLivePreviewSpec.Image -> listOf(range.toTextRange())
  }
}

/** What one owned fold region should look like. */
private sealed interface OwnedRegion {
  data class Text(val placeholderText: String) : OwnedRegion
  data object HorizontalRule : OwnedRegion
  data class Image(val destination: String, val elementsHash: Int) : OwnedRegion

  fun matches(region: FoldRegion): Boolean {
    return when (this) {
      is Text -> region !is CustomFoldRegion && region.placeholderText == placeholderText
      HorizontalRule -> region !is CustomFoldRegion && region.placeholderText.isEmpty()
      is Image -> (region as? CustomFoldRegion)?.markdownImageRenderItem()?.destination == destination
    }
  }
}

/**
 * Paints a Markdown horizontal rule without participating in editor layout or input handling.
 *
 * The source line is concealed by a zero-width, never-expanding fold. A range highlighter is used only as
 * a paint hook: unlike an inlay or a line marker, it does not reserve height, change hit testing, consume
 * mouse events, or install an editor component. This keeps the normal caret and selection machinery in
 * charge of revealing the source when the line is touched.
 *
 * The rule spans the visible editor area rather than the highlighter's range, which may be soft-wrapped or
 * scrolled out of view. Geometry and color are read on every paint, so ordinary repainting covers scrolling,
 * resizing, font changes and color-scheme changes. The stripe sits at the center of the visual line and so
 * adds no line height.
 */
private object MarkdownHorizontalRuleRenderer : CustomHighlighterRenderer {
  override fun paint(editor: Editor, highlighter: RangeHighlighter, graphics: Graphics) {
    if (editor.isDisposed || !highlighter.isValid) return
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.width <= 0) return
    val visualLine = editor.offsetToVisualPosition(highlighter.startOffset).line
    val y = editor.visualLineToY(visualLine) + editor.lineHeight / 2
    val scheme = editor.colorsScheme
    val color = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
      ?: highlighter.getTextAttributes(scheme)?.foregroundColor
      ?: scheme.defaultForeground
    val child = (graphics as? Graphics2D)?.create() as? Graphics2D ?: return
    try {
      child.color = color
      LinePainter2D.paint(
        child, visibleArea.x.toDouble(), y.toDouble(),
        (visibleArea.x + visibleArea.width).toDouble(), y.toDouble()
      )
    }
    finally {
      child.dispose()
    }
  }
}
