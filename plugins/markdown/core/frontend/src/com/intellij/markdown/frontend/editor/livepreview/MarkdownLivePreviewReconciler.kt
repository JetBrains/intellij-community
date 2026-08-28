// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.codeInsight.documentation.render.DocRenderItemUpdater
import com.intellij.codeInsight.documentation.render.DocRenderer
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
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpec
import org.intellij.plugins.markdown.editor.livepreview.MarkdownLivePreviewSpecSet
import org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus
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
   * The regions we own, keyed by the concealed range each was created for. The keys go stale as soon as the
   * document changes - regions move with the text - so [reconcileNow] re-keys them from the regions
   * themselves, and every path that trusts the keys first checks the spec set against the document stamp.
   */
  private val ownedRegions = HashMap<TextRange, FoldRegion>()
  private val ownedHorizontalRules = HashMap<TextRange, RangeHighlighter>()
  private val imageManager = MarkdownLivePreviewImageManager(project, editor)

  /** Indices of the elements we have already revealed, so [revealNow] can act on the difference alone. */
  private var revealedElements = emptySet<Int>()

  /** Set while we are mutating folds ourselves, so our own listeners do not reenter. */
  private var updating = false

  private var reconcileScheduled = false
  private var disposed = false

  init {
    installListeners()
  }

  @Suppress("ObjectLiteralToLambda")
  private fun installListeners() {
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) = onCaretChanged()
      override fun caretAdded(event: CaretEvent) = onCaretChanged()
      override fun caretRemoved(event: CaretEvent) = onCaretChanged()
    }, this)
    editor.selectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(event: SelectionEvent) = onCaretChanged()
    }, this)
    editor.scrollingModel.addVisibleAreaListener(object : VisibleAreaListener {
      override fun visibleAreaChanged(e: VisibleAreaEvent) = imageManager.updateGeometry()
    }, this)

    val connection = project.messageBus.connect(this)
    connection.subscribe(MarkdownSettings.ChangeListener.TOPIC, object : MarkdownSettings.ChangeListener {
      override fun settingsChanged(settings: MarkdownSettings) = scheduleReconcile()
    })
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        imageManager.invalidate(events) { scheduleReconcile() }
        scheduleReconcile()
      }
    })
  }

  /**
   * Hands over the specs computed for one state of the document and brings the editor in line with them.
   * Called on the EDT once the highlighting pass has finished computing.
   */
  @RequiresEdt
  fun publishSpecs(specSet: MarkdownLivePreviewSpecSet) {
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
    if (!isLivePreviewEnabled()) {
      removeAllOwned()
      return
    }
    val specSet = this.specSet ?: return
    if (specSet.documentStamp != editor.document.modificationStamp) return

    val revealed = revealedElementIndices(specSet)
    val (regions, lines) = buildOwnedState(specSet, revealed)
    reconcileConcealRegions(regions)
    reconcileLineRegions(lines)
    reconcileImageRegions()
    revealedElements = revealed
  }

  private fun buildOwnedState(specSet: MarkdownLivePreviewSpecSet, revealed: Set<Int>): OwnedState {
    val regions = HashMap<TextRange, OwnedRegion>()
    val lines = HashSet<TextRange>()
    specSet.elements.forEachIndexed { index, spec ->
      if (index in revealed) return@forEachIndexed
      when (spec) {
        is MarkdownLivePreviewSpec.Conceal -> spec.conceals.forEach { regions[it] = OwnedRegion.Text(null) }
        is MarkdownLivePreviewSpec.HorizontalRule -> {
          regions[spec.range] = OwnedRegion.HorizontalRule
          lines.add(spec.range)
        }
        is MarkdownLivePreviewSpec.Image -> {
          val source = imageManager.load(spec) { scheduleReconcile() } ?: return@forEachIndexed
          regions[spec.range] = OwnedRegion.Image(source)
        }
        is MarkdownLivePreviewSpec.Bullet -> regions[spec.concealRange] = OwnedRegion.Text(spec.placeholderText)
      }
    }
    return OwnedState(regions, lines)
  }

  private fun reconcileConcealRegions(desired: Map<TextRange, OwnedRegion>) {
    // Re-key by where the regions actually are now, which is how a region survives an edit that only
    // shifted it, and how a region the clipboard brought back gets picked up instead of duplicated.
    val existing = HashMap<TextRange, FoldRegion>()
    for (region in ownedRegions.values) {
      if (region.isValid) existing[region.currentRange()] = region
    }

    val toRemove = existing.entries
      .filter { (range, region) ->
        val wanted = desired[range]
        wanted == null || !wanted.matches(region)
      }
      .map { it.value }
    val toAdd = desired.filter { (range, wanted) ->
      val existingRegion = existing[range]
      existingRegion == null || !wanted.matches(existingRegion)
    }
    val next = HashMap(existing)
    if (toRemove.isNotEmpty() || toAdd.isNotEmpty()) {
      toRemove.forEach { next.remove(it.currentRange()) }
      if (toRemove.isNotEmpty()) {
        runFoldBatch {
          toRemove.forEach { editor.foldingModel.removeFoldRegion(it) }
        }
      }
      if (toAdd.isNotEmpty()) {
        runFoldBatch {
          for ((range, wanted) in toAdd) {
            val region = when (wanted) {
              is OwnedRegion.Text -> concealRange(range, wanted.placeholderText)
              is OwnedRegion.HorizontalRule -> concealRange(range, null)
              is OwnedRegion.Image -> createImageRegion(range, wanted.source)
            }
            if (region != null) next[range] = region
          }
        }
      }
    }
    ownedRegions.clear()
    ownedRegions.putAll(next)
  }

  private fun reconcileLineRegions(desired: Set<TextRange>) {
    val paintableRules = desired.filterTo(HashSet()) { range ->
      val region = ownedRegions[range] ?: return@filterTo false
      region.isValid && region.placeholderText.isEmpty()
    }
    val existingRules = HashMap<TextRange, RangeHighlighter>()
    for (highlighter in ownedHorizontalRules.values) {
      if (highlighter.isValid) existingRules[highlighter.textRange] = highlighter
    }
    val obsoleteRules = existingRules.filterKeys { it !in paintableRules }.values
    for (rule in obsoleteRules) {
      rule.dispose()
    }
    val nextRules = HashMap<TextRange, RangeHighlighter>()
    for (range in paintableRules) {
      val existingRule = existingRules[range]
      if (existingRule != null) {
        nextRules[range] = existingRule
      }
      else {
        val newRule = createHorizontalRule(range)
        if (newRule != null) nextRules[range] = newRule
      }
    }
    ownedHorizontalRules.clear()
    ownedHorizontalRules.putAll(nextRules)
  }

  // Deliberately not @RequiresEdt: Disposer may call this from any thread, and it only drops state.
  override fun dispose() {
    disposed = true
    ownedRegions.clear()
    ownedHorizontalRules.clear()
    revealedElements = emptySet()
    specSet = null
    Disposer.dispose(imageManager)
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
    if (!isLivePreviewEnabled()) return

    val revealed = revealedElementIndices(specSet)
    val newlyRevealed = revealed - revealedElements
    if (newlyRevealed.isEmpty()) return

    val rangesToRemove = HashSet<TextRange>()
    for (index in newlyRevealed) {
      when (val spec = specSet.elements[index]) {
        is MarkdownLivePreviewSpec.Conceal -> rangesToRemove.addAll(spec.conceals)
        is MarkdownLivePreviewSpec.HorizontalRule -> rangesToRemove.add(spec.range)
        is MarkdownLivePreviewSpec.Image -> rangesToRemove.add(spec.range)
        is MarkdownLivePreviewSpec.Bullet -> rangesToRemove.add(spec.concealRange)
      }
    }
    revealOwnedRegions(rangesToRemove)
    revealOwnedHorizontalRules(rangesToRemove)
    revealedElements = revealedElements + newlyRevealed
  }

  private fun revealOwnedRegions(ranges: Set<TextRange>) {
    val toRemove = ArrayList<Pair<TextRange, FoldRegion>>()
    for (range in ranges) {
      val region = ownedRegions[range] ?: continue
      if (region.isValid) toRemove.add(range to region)
    }
    if (toRemove.isEmpty()) return
    runFoldBatch { toRemove.forEach { editor.foldingModel.removeFoldRegion(it.second) } }
    toRemove.forEach { ownedRegions.remove(it.first) }
  }

  private fun revealOwnedHorizontalRules(ranges: Set<TextRange>) {
    val toRemove = ownedHorizontalRules.filter { (range, highlighter) ->
      range in ranges && highlighter.isValid
    }
    toRemove.values.forEach { it.dispose() }
    toRemove.keys.forEach { ownedHorizontalRules.remove(it) }
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
  private fun revealedElementIndices(specSet: MarkdownLivePreviewSpecSet): Set<Int> {
    val revealed = HashSet<Int>()
    for (caret in editor.caretModel.allCarets) {
      specSet.intersecting(caret.selectionStart, caret.selectionEnd, revealed)
    }
    return revealed
  }

  private fun concealRange(range: TextRange, placeholderText: String?): FoldRegion? {
    val foldingModel = editor.foldingModel
    val foldedText = placeholderText.orEmpty()
    val existing = foldingModel.getFoldRegion(range.startOffset, range.endOffset)
    if (existing != null) {
      // A region is already here, most likely restored from the clipboard together with pasted text. The
      // folding model refuses a second region over the same range, so adopt this one instead.
      if (!existing.isValid || existing is CustomFoldRegion || !existing.shouldNeverExpand() ||
          existing.placeholderText != foldedText) {
        return null
      }
      if (placeholderText != null) existing.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true)
      return existing
    }
    // Can come back null for reasons beyond a caret being in the way - folding switched off, or a boundary
    // that would split a character pair - so never assume the region exists.
    val region = foldingModel.createFoldRegion(range.startOffset, range.endOffset, foldedText, null, true) ?: return null
    if (placeholderText != null) region.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true)
    return region
  }

  private fun createImageRegion(range: TextRange, source: VirtualFile): CustomFoldRegion? {
    if (range.isEmpty) return null
    val document = editor.document
    val item = imageManager.createItem(range, source)
    val region = editor.foldingModel.addCustomLinesFolding(
      document.getLineNumber(range.startOffset),
      document.getLineNumber((range.endOffset - 1).coerceAtLeast(range.startOffset)),
      item.renderer
    )
    if (region == null) {
      item.dispose()
      return null
    }
    item.foldRegion = region
    DocRenderItemUpdater.updateRenderers(listOf(item), false)
    return region
  }

  @RequiresEdt
  fun handleBackspace(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    if (disposed || editor.isDisposed || editor.selectionModel.hasSelection()) return false
    val caretOffset = editor.caretModel.offset
    val document = editor.document
    val foldRegion = findImageFoldRegion(caretOffset, document) ?: return false
    revealOwnedRegions(setOf(foldRegion.currentRange()))
    val deleteStart = if (foldRegion.endOffset == caretOffset) caretOffset else caretOffset - 1
    runWriteAction { document.deleteString(deleteStart, deleteStart + 1) }
    return true
  }

  private fun findImageFoldRegion(caretOffset: Int, document: Document): CustomFoldRegion? {
    val imageEndOffset = when {
      caretOffset > 0 && document.charsSequence[caretOffset - 1] == '\n' -> caretOffset - 1
      caretOffset < document.textLength && document.charsSequence[caretOffset] == '\n' -> caretOffset
      else -> return null
    }
    val foldRegion = ownedRegions.values.asSequence()
      .filterIsInstance<CustomFoldRegion>()
      .filter { it.isValid }
      .filter { it.endOffset == imageEndOffset }
      .firstOrNull() ?: return null
    val renderer = foldRegion.renderer as? DocRenderer ?: return null
    return foldRegion.takeIf { renderer.item is MarkdownImageRenderItem }
  }

  private fun reconcileImageRegions() {
    val regions = ownedRegions.values.filterIsInstance<CustomFoldRegion>().filter { it.isValid }
    regions.forEach { it.repaint() }
  }

  private fun removeAllOwned() {
    val regions = ownedRegions.values.filter { it.isValid }
    ownedHorizontalRules.values.filter { it.isValid }.forEach { it.dispose() }
    ownedRegions.clear()
    ownedHorizontalRules.clear()
    revealedElements = emptySet()
    if (regions.isEmpty()) return
    runFoldBatch { regions.forEach { editor.foldingModel.removeFoldRegion(it) } }
  }

  private fun createHorizontalRule(range: TextRange): RangeHighlighter? {
    if (range.isEmpty) return null
    val highlighter = editor.markupModel.addRangeHighlighter(
      MarkdownHighlighterColors.HRULE, range.startOffset, range.endOffset,
      HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE,
    )
    highlighter.customRenderer = MarkdownHorizontalRuleRenderer
    return highlighter
  }

  /** Runs one fold batch while preserving each caret and the viewport anchor. */
  private fun runFoldBatch(body: () -> Unit) {
    val snapshot = editor.caretModel.allCarets.map { CaretSnapshot(it) }
    updating = true
    try {
      EditorScrollingPositionKeeper.perform(editor, false) {
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

    /** The reconciler for [editor], attaching one on first use. Null in cases a reconciler can't be created. */
    @RequiresEdt
    fun getOrCreate(editor: Editor): MarkdownLivePreviewReconciler? {
      ThreadingAssertions.assertEventDispatchThread()
      if (editor !is EditorEx || editor.isDisposed) return null
      val project = editor.project ?: return null
      val existing = editor.getUserData(KEY)
      if (existing != null) return existing
      val reconciler = MarkdownLivePreviewReconciler(project, editor)
      editor.putUserData(KEY, reconciler)
      Disposer.register(reconciler) { editor.putUserData(KEY, null) }
      EditorUtil.disposeWithEditor(editor, reconciler)
      return reconciler
    }

    fun getExisting(editor: Editor): MarkdownLivePreviewReconciler? = editor.getUserData(KEY)
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
 * The renderer cannot use the highlighter's logical range to determine the stripe width. The source may be
 * longer than the viewport, may be soft-wrapped, and may be horizontally scrolled; the requested rule is
 * always the width of the currently visible editor area. Likewise, geometry and color are read on every
 * paint so ordinary editor repainting reflects scrolling, resizing, font changes, and color-scheme changes
 * without dedicated listeners or cached state.
 *
 * The vertical position is the center of the existing visual line, so the stripe adds no line height. A
 * child graphics context isolates the renderer's color and rendering state from other editor painters.
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

private data class OwnedState(
  val regions: Map<TextRange, OwnedRegion>,
  val lines: Set<TextRange>,
)

private sealed interface OwnedRegion {
  fun matches(region: FoldRegion): Boolean

  data object HorizontalRule : OwnedRegion {
    override fun matches(region: FoldRegion): Boolean = region !is CustomFoldRegion && region.placeholderText.isEmpty()
  }

  data class Text(val placeholderText: String?) : OwnedRegion {
    override fun matches(region: FoldRegion): Boolean {
      return region !is CustomFoldRegion && region.placeholderText == placeholderText.orEmpty()
    }
  }

  data class Image(val source: VirtualFile) : OwnedRegion {
    override fun matches(region: FoldRegion): Boolean {
      val customRegion = region as? CustomFoldRegion ?: return false
      val renderer = customRegion.renderer as? DocRenderer ?: return false
      val item = renderer.item as? MarkdownImageRenderItem ?: return false
      return item.source == source
    }
  }
}
