// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.session

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.terminal.tests.reworked.frontend.session.ketraterm.retainedText
import com.intellij.terminal.frontend.session.ketraterm.KetraTermTerminalSession
import org.assertj.core.api.Assertions.assertThat
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.ketraterm.KetraTermTerminalView
import com.intellij.terminal.tests.reworked.util.LoopbackTtyConnector
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.EditorTestUtil
import com.intellij.util.asDisposable
import com.sun.management.ThreadMXBean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.intellij.find.FindModel
import com.intellij.find.impl.livePreview.SearchResults
import com.intellij.terminal.frontend.view.impl.TerminalSearchSession
import java.awt.event.ComponentEvent
import io.github.ketraterm.ui.swing.api.SwingTerminal
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.TerminalEmulatorType
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalCursorOffsetChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.RepaintManager
import kotlin.math.ceil
import kotlin.math.max

internal const val CURSOR_BURST_NAME = "cursor-burst-10000-writes"

/** Owns the production views and the reusable image for terminal benchmarks. */
internal class TerminalRenderingBenchmark(private val project: Project) {
  suspend fun createHarness(
    parentScope: CoroutineScope,
    backend: TerminalEmulatorType,
    observer: PaintObserver,
  ): PaintHarness {
    val scope = parentScope.childScope("Rendering benchmark $backend", Dispatchers.Default)
    val viewScope = scope.childScope("Rendering benchmark frontend", Dispatchers.Default)
    val (session, connector) = TerminalSessionTestUtil.createLoopbackTerminalSession(
      project, scope, emulatorType = backend,
    )
    try {
      return withContext(Dispatchers.EDT) {
        val settings = JBTerminalSystemSettingsProvider()
        val view: TerminalView = if (backend == TerminalEmulatorType.KetraTerm) {
          KetraTermTerminalView(project, settings, viewScope)
        }
        else TerminalViewImpl(project, settings, null, viewScope)
        when (view) {
          is KetraTermTerminalView -> view.connectToSession(session)
          is TerminalViewImpl -> view.connectToSession(session)
        }
        val editor = (view as? TerminalViewImpl)?.outputEditor
        val component: JComponent
        val size: Dimension
        if (view is KetraTermTerminalView) {
          component = view.terminal
          configureKetraTermBenchmarkGrid(component, session as KetraTermTerminalSession, COLUMNS, ROWS)
          size = component.size
        }
        else {
          editor!!
          val grid = checkNotNull(editor.characterGrid)
          size = Dimension(ceil(COLUMNS * grid.charWidth).toInt(), ROWS * editor.lineHeight)
          EditorTestUtil.setEditorVisibleSizeInPixels(editor, size.width, size.height)
          editor.scrollingModel.disableAnimation()
          component = editor.contentComponent
          component.size = size
        }
        val harness = PaintHarness(
          project, scope, viewScope, connector, component, editor, size, observer, backend.name, view,
        )
        if (editor != null) {
          val model = view.outputModels.regular
          harness.copyRetainedText = { model.getText(model.startOffset, model.endOffset).toString() }
          harness.retainedTextLength = { model.textLength }
          harness.retainedLastLine = {
            val line = model.lastLineIndex
            model.getText(model.getStartOfLine(line), model.getEndOfLine(line))
          }
          model.addListener(scope.asDisposable(), object : TerminalOutputModelListener {
            private var updateStarted = 0L
            override fun beforeContentChanged(model: TerminalOutputModel) {
              if (harness.diagnostics) updateStarted = System.nanoTime()
            }
            override fun afterContentChanged(event: TerminalContentChangeEvent) {
              if (harness.diagnostics) harness.recordDocumentUpdate(event, updateStarted)
              harness.contentUpdated()
              harness.interactiveStateChanged()
              harness.requestPaint()
            }
            override fun cursorOffsetChanged(event: TerminalCursorOffsetChangeEvent) {
              harness.interactiveStateChanged()
            }
          })
        }
        if (session is KetraTermTerminalSession) {
          harness.nativeSession = session
          harness.copyRetainedText = session::retainedText
          if (harness.diagnostics) scope.launch(Dispatchers.Unconfined) {
            session.terminalSession.renderGeneration.collect { harness.observeScrollPublication() }
          }
          scope.launch(Dispatchers.EDT) {
            session.terminalSession.renderGeneration.collect { harness.observeNative() }
          }
        }
        println("RENDER VIEW ${backend.name}: ${size.width}x${size.height}; ${component.javaClass.name}; " +
                "font=${settings.terminalFont}")
        harness
      }
    }
    catch (error: Throwable) {
      com.intellij.diagnostic.rethrowControlFlowException(error)
      connector.close()
      withContext(NonCancellable) { scope.coroutineContext.job.cancelAndJoin() }
      throw error
    }
  }

  private class PaintProbe(
    val rgb: Int,
    val expected: String?,
    val token: String,
    val clear: Boolean,
    val canonicalHistory: Boolean,
    val measureAllocations: Boolean,
  ) {
    var markerBytes = -1L
    var historyBytes = -1L
    val arrival = CompletableDeferred<Long>()
    val historyReady = CompletableDeferred<Long>()
    val visibleReady = CompletableDeferred<Long>()
    var documentMarkerAt = 0L
    var documentWork = 0L
    var documentUpdates = 0
    var documentInsertedUtf16 = 0L
    var documentUpdatesBeforePaint = 0
    var documentInsertedUtf16BeforePaint = 0L
    var documentUpdatesAtHistory = 0
    var documentInsertedUtf16AtHistory = 0L
    var paints = 0
    var paintWork = 0L
    var finalPaintStarted = 0L
    var finalPaintWork = 0L
    @Volatile var firstPublicationAt = 0L
    @Volatile var markerPublicationAt = 0L
    @Volatile var publicationCount = 0
  }

  private class ScrollTrace(val start: Long, val previousPublication: Long) {
    @Volatile var published = 0L
    var returned = 0L
    var edtObserved = 0L
    var repaintRequested = 0L
    var paintStarted = 0L
    var paintFinished = 0L
    var documentUpdates = 0
    var documentWork = 0L
  }

  class PaintObserver : RepaintManager() {
    @Volatile var active: PaintHarness? = null

    init { isDoubleBufferingEnabled = false }

    override fun addDirtyRegion(component: JComponent, x: Int, y: Int, width: Int, height: Int) {
      super.addDirtyRegion(component, x, y, width, height)
      val harness = active ?: return
      if (component === harness.component) harness.repaintRequested(x, y, width, height)
    }
  }

  class PaintHarness(
    private val project: Project,
    private val scope: CoroutineScope,
    private val viewScope: CoroutineScope,
    private val connector: LoopbackTtyConnector,
    val component: JComponent,
    private val editor: EditorImpl?,
    size: Dimension,
    private val observer: PaintObserver,
    private val backend: String,
    private val view: TerminalView,
  ) {
    val diagnostics = java.lang.Boolean.getBoolean("ketraterm.benchmark.paintDiagnostics")
    private val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    private val graphics: Graphics2D = image.createGraphics()
    private val pixels = (image.raster.dataBuffer as DataBufferInt).data
    private val queued = AtomicBoolean()
    @Volatile private var probe: PaintProbe? = null
    @Volatile private var partialPaint: PartialPaint? = null
    private var closed = false
    private class InteractiveUpdate(val row: Int, val column: Int, val expectedPixels: IntArray?) {
      val completed = CompletableDeferred<Long>()
      var allocatedAtCompletion = 0L

      fun finish(time: Long) {
        allocatedAtCompletion = allocationBean.totalThreadAllocatedBytes
        completed.complete(time)
      }
    }

    data class InteractiveSample(val nanos: Long, val bytes: Long)

    private var interactive: InteractiveUpdate? = null
    private var realizedRoot: java.awt.Component? = null
    var nativeSession: KetraTermTerminalSession? = null
    var visibleNanos = 0L
      private set
    var historyNanos = 0L
      private set
    var documentHistoryNanos = 0L
      private set
    @Volatile private var scrollTrace: ScrollTrace? = null
    @Volatile private var lastPublication = 0L
    private var expectedRetained: String? = null
    private var operationPaint: CompletableDeferred<Long>? = null
    private var operationQuery: String? = null
    private var operationQueryOffset = -1
    private var editorSearch: TerminalSearchSession? = null
    private var editorSearchResults: SearchResults? = null
    private var searchReady: CompletableDeferred<Long>? = null
    private var historyPaint: CompletableDeferred<Long>? = null
    private var lastMarkerRgb = 0
    var copyRetainedText: (() -> String)? = null
    var retainedTextLength: (() -> Int)? = null
    var retainedLastLine: (() -> CharSequence)? = null
    var markerAllocatedBytes = 0L
      private set
    var historyAllocatedBytes = 0L
      private set
    var paintNanos = 0L
      private set
    var paintBytes = 0L
      private set
    private val painter = Runnable {
      queued.set(false)
      val update = interactive
      if (update != null) {
        if (!update.completed.isCompleted && cursorMatches(update)) {
          val expected = update.expectedPixels
          if (expected != null) {
            paintInteractiveScreen()
            val paintedAt = System.nanoTime()
            if (Arrays.equals(pixels, 0, expected.size, expected, 0, expected.size)) update.finish(paintedAt)
          }
        }
        return@Runnable
      }
      if (partialPaint != null) {
        paintPartialUpdate()
        return@Runnable
      }
      val operation = operationPaint
      if (!closed && operation != null && !operation.isCompleted && operationViewportReady()) {
        paint()
        operation.complete(System.nanoTime())
      }
      val history = historyPaint
      if (!closed && history != null && !history.isCompleted && oldestViewportReady()) {
        scrollTrace?.paintStarted = System.nanoTime()
        paint()
        val completed = System.nanoTime()
        if (!containsColor(lastMarkerRgb)) {
          scrollTrace?.paintFinished = completed
          history.complete(completed)
        }
      }
      val current = probe
      if (!closed && current != null && !current.arrival.isCompleted) {
        observeNative()
        prepareViewport()
        val paintStarted = if (diagnostics) System.nanoTime() else 0L
        paint()
        val completed = System.nanoTime()
        if (diagnostics) {
          current.paints++
          current.paintWork += completed - paintStarted
        }
        if (current.visibleReady.isCompleted && containsColor(current.rgb)) {
          current.finalPaintStarted = paintStarted
          current.finalPaintWork = completed - paintStarted
          if (diagnostics) {
            current.documentUpdatesBeforePaint = current.documentUpdates
            current.documentInsertedUtf16BeforePaint = current.documentInsertedUtf16
          }
          if (current.measureAllocations) current.markerBytes = allocationBean.totalThreadAllocatedBytes
          current.arrival.complete(completed)
        }
      }
    }

    fun requestPaint() {
      if (partialPaint?.let { it.region == null } == true) return
      val trace = scrollTrace
      if (trace != null && trace.repaintRequested == 0L && javax.swing.SwingUtilities.isEventDispatchThread() && oldestViewportReady()) {
        trace.repaintRequested = System.nanoTime()
      }
      if (queued.compareAndSet(false, true)) {
        ApplicationManager.getApplication().invokeLater(painter, ModalityState.any())
      }
    }

    /** Enables normal Swing repaint requests without creating a desktop window. */
    fun enableRequestedRepaints() {
      check(javax.swing.SwingUtilities.isEventDispatchThread())
      var root: java.awt.Component = component
      while (root.parent != null) root = root.parent
      check(!root.isDisplayable)
      root.addNotify()
      realizedRoot = root
      check(component.isShowing)
    }

    data class PartialUpdate(val payload: String, val rgb: Int)

    data class PartialResult(
      val batchNanos: Long,
      val latencies: LongArray,
      val paintNanos: Long,
      val paintBytes: Long,
      val allocatedBytes: Long,
      val paintCalls: Long,
      val paintedPixels: Long,
      val viewportPixels: Long,
    )

    private class PartialPaint(val rgb: Int) {
      val arrival = CompletableDeferred<Long>()
      var region: Rectangle? = null
      var paintNanos = 0L
      var paintBytes = 0L
      var paintCalls = 0L
      var paintedPixels = 0L
      var lastRegion: Rectangle? = null
    }

    fun repaintRequested(x: Int, y: Int, width: Int, height: Int) {
      if (partialPaint == null) {
        requestPaint()
        return
      }
      if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
        ApplicationManager.getApplication().invokeLater({ repaintRequested(x, y, width, height) }, ModalityState.any())
        return
      }
      val current = partialPaint
      if (current != null && !current.arrival.isCompleted) {
        val offset = editor?.scrollingModel?.visibleArea?.y ?: 0
        val region = Rectangle(x, y - offset, width, height).intersection(Rectangle(0, 0, image.width, image.height))
        if (region.isEmpty) return
        current.region?.add(region) ?: run { current.region = region }
      }
      requestPaint()
    }

    private fun paintPartialUpdate() {
      val current = partialPaint ?: return
      if (closed || current.arrival.isCompleted) return
      val region = current.region ?: return
      current.region = null
      current.lastRegion = region
      val clip = region
      val thread = Thread.currentThread().threadId()
      val bytesBefore = allocationBean.getThreadAllocatedBytes(thread)
      val start = System.nanoTime()
      paint(clip)
      val completed = System.nanoTime()
      current.paintNanos += completed - start
      current.paintBytes += allocationBean.getThreadAllocatedBytes(thread) - bytesBefore
      current.paintCalls++
      current.paintedPixels += clip.width.toLong() * clip.height
      if (containsColor(current.rgb)) current.arrival.complete(completed)
    }

    /** Measures successive updates and verifies the final image after the timed batch. */
    suspend fun submitPartialUpdates(updates: List<PartialUpdate>): PartialResult {
      require(updates.isNotEmpty())
      val probes = updates.map { PartialPaint(it.rgb) }
      val latencies = LongArray(updates.size)
      var start = 0L
      var completed = 0L
      var allocatedBefore = 0L
      var allocatedAfter = 0L
      try {
        for (index in updates.indices) {
          val current = probes[index]
          withContext(Dispatchers.EDT) {
            check(!containsColor(current.rgb))
            partialPaint = current
            observer.active = this@PaintHarness
          }
          if (index == 0) allocatedBefore = allocationBean.totalThreadAllocatedBytes
          val submitted = System.nanoTime()
          if (index == 0) start = submitted
          connector.feed(updates[index].payload)
          completed = current.arrival.await()
          latencies[index] = completed - submitted
        }
        allocatedAfter = allocationBean.totalThreadAllocatedBytes
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) {
          val current = partialPaint
          if (current != null && !current.arrival.isCompleted) {
            println("PARTIAL INCOMPLETE $backend calls=${current.paintCalls} region=${current.lastRegion} " +
                    "showing=${component.isShowing} visible=${editor?.scrollingModel?.visibleArea}")
          }
          partialPaint = null
        }
      }
      return withContext(Dispatchers.EDT) {
        val partial = pixels.copyOf()
        paint()
        val difference = pixels.indices.firstOrNull { pixels[it] != partial[it] }
        check(difference == null) {
          "The requested regions differ from a full paint: backend=$backend pixel=$difference " +
          "region=${probes.last().lastRegion} visible=${editor?.scrollingModel?.visibleArea}"
        }
        check(allocatedAfter >= allocatedBefore)
        PartialResult(completed - start, latencies, probes.sumOf { it.paintNanos }, probes.sumOf { it.paintBytes },
                      allocatedAfter - allocatedBefore, probes.sumOf { it.paintCalls }, probes.sumOf { it.paintedPixels },
                      image.width.toLong() * image.height)
      }
    }

    suspend fun submit(
      payload: String,
      sequence: Int,
      verifyHistory: Boolean = false,
      expectedTextBeforeMarker: String? = null,
      canonicalHistory: Boolean = false,
      measureAllocations: Boolean = false,
    ): Long {
      require(!measureAllocations || (verifyHistory && !canonicalHistory))
      val rgb = 0xFF000000.toInt() or (0x234000 + sequence)
      val red = rgb ushr 16 and 255
      val green = rgb ushr 8 and 255
      val blue = rgb and 255
      val marker = "\r\n\u001b[48;2;$red;$green;${blue}m\u001b[38;2;255;255;255mPAINT$sequence        \u001b[0m"
      // Reused samples must clear both renderer and compatibility text before the next identical payload.
      val expected = if (payload == CLEAR) "\nPAINT$sequence        "
      else if (verifyHistory) {
        val beforeMarker = expectedTextBeforeMarker ?: textAfterErasingMarker(payload.removePrefix(ERASE_MARKER))
        beforeMarker + "\nPAINT$sequence        "
      }
      else null
      val current = PaintProbe(rgb, expected, "PAINT$sequence", payload == CLEAR, canonicalHistory, measureAllocations)
      withContext(Dispatchers.EDT) {
        check(!containsColor(rgb)) { "The new marker already exists in the painted image" }
        probe = current
        observer.active = this@PaintHarness
      }
      lastMarkerRgb = rgb
      val allocatedBefore = if (measureAllocations) allocationBean.totalThreadAllocatedBytes else 0L
      val start = System.nanoTime()
      connector.feed(payload)
      connector.feed(marker)
      val latency = current.arrival.await() - start
      if (expected != null) {
        withContext(Dispatchers.EDT) { contentUpdated() }
        val completeAt = try {
          current.historyReady.await()
        }
        catch (error: kotlinx.coroutines.CancellationException) {
          withContext(NonCancellable + Dispatchers.EDT) {
            val actual = historyText(requireNotNull(copyRetainedText).invoke(), canonicalHistory)
            val wanted = historyText(expected, canonicalHistory)
            val difference = (0 until minOf(actual.length, wanted.length)).firstOrNull { actual[it] != wanted[it] }
              ?: minOf(actual.length, wanted.length)
            println("HISTORY INCOMPLETE $backend expected=${wanted.length} actual=${actual.length} difference=$difference " +
                    "expectedCodes=${wanted.drop(difference).take(24).map { it.code.toString(16) }} " +
                    "actualCodes=${actual.drop(difference).take(24).map { it.code.toString(16) }}")
          }
          throw error
        }
        val visibleAt = current.visibleReady.await()
        visibleNanos = visibleAt - start
        historyNanos = (if (nativeSession != null) visibleAt else completeAt) - start
        documentHistoryNanos = if (retainedLastLine != null) completeAt - start else 0L
        assertThat(visibleNanos).isLessThanOrEqualTo(latency)
        if (measureAllocations) {
          markerAllocatedBytes = current.markerBytes - allocatedBefore
          historyAllocatedBytes = current.historyBytes - allocatedBefore
          check(markerAllocatedBytes >= 0 && historyAllocatedBytes >= 0)
        }
        withContext(Dispatchers.EDT) {
          if (current.clear) assertThat(requireNotNull(copyRetainedText).invoke().trim()).isEqualTo(current.token)
          else assertThat(historyText(requireNotNull(copyRetainedText).invoke(), canonicalHistory)).isEqualTo(historyText(expected, canonicalHistory))
        }
      }
      if (diagnostics) {
        withContext(Dispatchers.EDT) {
          val documentMarker = if (current.documentMarkerAt == 0L) -1.0 else (current.documentMarkerAt - start) / 1_000_000.0
          val firstPublication = if (current.firstPublicationAt == 0L) -1.0 else (current.firstPublicationAt - start) / 1_000_000.0
          val markerPublication = if (current.markerPublicationAt == 0L) -1.0 else (current.markerPublicationAt - start) / 1_000_000.0
          println("PAINT TRACE $backend sequence=$sequence elapsedMs=${latency / 1_000_000.0} " +
                  "publications=${current.publicationCount} " +
                  "firstPublicationMs=$firstPublication markerPublicationMs=$markerPublication " +
                  "documentMarkerMs=$documentMarker documentWorkBeforePaintMs=${current.documentWork / 1_000_000.0} " +
                  "documentUpdatesBeforePaint=${current.documentUpdatesBeforePaint} " +
                  "documentInsertedUtf16BeforePaint=${current.documentInsertedUtf16BeforePaint} " +
                  "documentUpdatesAtHistory=${current.documentUpdatesAtHistory} " +
                  "documentInsertedUtf16AtHistory=${current.documentInsertedUtf16AtHistory} " +
                  "paintCalls=${current.paints} allPaintWorkMs=${current.paintWork / 1_000_000.0} " +
                  "finalPaintStartMs=${(current.finalPaintStarted - start) / 1_000_000.0} " +
                  "finalPaintWorkMs=${current.finalPaintWork / 1_000_000.0}")
        }
      }
      withContext(Dispatchers.EDT) {
        expectedRetained = if (current.clear) null else expected
        probe = null
      }
      return latency
    }

    fun recordDocumentUpdate(event: TerminalContentChangeEvent, started: Long) {
      scrollTrace?.let {
        it.documentUpdates++
        it.documentWork += System.nanoTime() - started
      }
      val current = probe ?: return
      val now = System.nanoTime()
      current.documentUpdates++
      current.documentInsertedUtf16 += event.newText.length
      if (!current.arrival.isCompleted) current.documentWork += now - started
      if (current.documentMarkerAt == 0L) {
        val model = event.model
        val lastLine = model.lastLineIndex
        if (model.getText(model.getStartOfLine(lastLine), model.getEndOfLine(lastLine)).contains(current.token)) {
          current.documentMarkerAt = System.nanoTime()
        }
      }
    }

    private fun historyText(text: String, canonical: Boolean): String =
      if (canonical) java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC) else text

    private fun completeHistory(current: PaintProbe, now: Long) {
      if (current.historyReady.isCompleted) return
      if (diagnostics) {
        current.documentUpdatesAtHistory = current.documentUpdates
        current.documentInsertedUtf16AtHistory = current.documentInsertedUtf16
      }
      if (current.measureAllocations) current.historyBytes = allocationBean.totalThreadAllocatedBytes
      current.historyReady.complete(now)
    }

    fun contentUpdated() {
      val current = probe ?: return
      if (current.clear) {
        if (copyRetainedText?.invoke()?.trim() == current.token) {
          val now = System.nanoTime()
          if (nativeSession == null) current.visibleReady.complete(now)
          completeHistory(current, now)
        }
        return
      }
      val lastLine = retainedLastLine ?: return
      if (!lastLine().contains(current.token)) return
      val now = System.nanoTime()
      if (nativeSession == null) current.visibleReady.complete(now)
      val expected = current.expected ?: return
      if (current.canonicalHistory) {
        // JediTerm composes combining accents. Validate canonical text only after the timed paint.
        if (current.arrival.isCompleted && historyText(requireNotNull(copyRetainedText).invoke(), true) == historyText(expected, true)) {
          completeHistory(current, now)
        }
      }
      else if (retainedTextLength?.invoke() == expected.length) completeHistory(current, now)
    }

    fun observeScrollPublication() {
      val session = nativeSession ?: return
      val now = System.nanoTime()
      lastPublication = now
      val current = probe
      if (current != null && current.markerPublicationAt == 0L) {
        session.terminalSession.readPublishedFrame { frame ->
          current.publicationCount++
          if (current.firstPublicationAt == 0L) current.firstPublicationAt = now
          if (frame.scrollbackOffset == 0 && frame.cursorRow in 0 until frame.rows) {
            val start = frame.cursorRow * frame.columns
            if (current.token.indices.all { frame.codeWords[start + it] == current.token[it].code }) {
              current.markerPublicationAt = now
            }
          }
        }
      }
      val trace = scrollTrace ?: return
      if (trace.published != 0L) return
      session.terminalSession.readPublishedFrame { frame ->
        if (frame.historySize > 0 && frame.scrollbackOffset == frame.historySize) trace.published = now
      }
    }

    fun observeNative() {
      interactiveStateChanged()
      val trace = scrollTrace
      if (trace != null && trace.edtObserved == 0L && oldestViewportReady()) trace.edtObserved = System.nanoTime()
      val session = nativeSession ?: return
      val current = probe ?: return
      if (current.visibleReady.isCompleted) return
      session.terminalSession.readPublishedFrame { frame ->
        if (frame.scrollbackOffset != 0 || frame.cursorRow !in 0 until frame.rows) return@readPublishedFrame
        val start = frame.cursorRow * frame.columns
        if (current.token.indices.any { frame.codeWords[start + it] != current.token[it].code }) return@readPublishedFrame
        val now = System.nanoTime()
        current.visibleReady.complete(now)
        if (retainedLastLine == null) completeHistory(current, now)
      }
    }

    /** Measures an immediate scrollbar jump after exact history verification. This is a separate timer. */
    suspend fun paintOldestHistory(): Long {
      val completed = CompletableDeferred<Long>()
      val start = withContext(Dispatchers.EDT) {
        historyPaint = completed
        val started = System.nanoTime()
        if (diagnostics) scrollTrace = ScrollTrace(started, lastPublication)
        if (editor != null) editor.scrollingModel.scrollVertically(0)
        else (component as SwingTerminal).scrollFromScrollbar(Int.MAX_VALUE, true)
        scrollTrace?.returned = System.nanoTime()
        requestPaint()
        started
      }
      val latency = completed.await() - start
      withContext(Dispatchers.EDT) {
        historyPaint = null
        scrollTrace?.let { trace ->
          fun millis(time: Long): Double = if (time == 0L) -1.0 else (time - trace.start) / 1_000_000.0
          println("SCROLL TRACE $backend startNanos=${trace.start} totalMs=${latency / 1_000_000.0} returnMs=${millis(trace.returned)} " +
                  "publishedMs=${millis(trace.published)} edtObservedMs=${millis(trace.edtObserved)} " +
                  "repaintMs=${millis(trace.repaintRequested)} paintStartMs=${millis(trace.paintStarted)} " +
                  "paintMs=${(trace.paintFinished - trace.paintStarted) / 1_000_000.0} " +
                  "previousPublishMs=${millis(trace.previousPublication)} documentUpdates=${trace.documentUpdates} " +
                  "documentWorkMs=${trace.documentWork / 1_000_000.0}")
        }
        scrollTrace = null
        assertThat(oldestViewportReady()).isTrue()
        // Text must produce pixels in the interior, apart from the scrollbar and component border.
        val background = pixels[image.width + image.width / 2]
        var ink = 0
        for (y in 30 until image.height - 30) {
          for (x in 30 until image.width - 30) if (pixels[y * image.width + x] != background) ink++
        }
        assertThat(ink).isGreaterThan(1000)
      }
      return latency
    }

    private fun oldestViewportReady(): Boolean {
      if (editor != null) return editor.scrollingModel.visibleArea.y == 0
      val terminal = component as SwingTerminal
      val viewport = terminal.viewportState()
      if (viewport.historySize == 0 || viewport.renderOffset != viewport.historySize) return false
      return nativeSession!!.terminalSession.readPublishedFrame { frame ->
        val prefix = "2026-08-07 12:34:56,000 [     0]"
        frame.scrollbackOffset == frame.historySize &&
        prefix.indices.all { frame.codeWords[frame.columns + it] == prefix[it].code }
      } == true
    }

    suspend fun returnToLive() = withContext(Dispatchers.EDT) {
      if (editor != null) prepareViewport()
      else (component as SwingTerminal).scrollFromScrollbar(0, true)
    }

    suspend fun selectHistory(): Long {
      val completed = CompletableDeferred<Long>()
      val start = withContext(Dispatchers.EDT) {
        operationPaint = completed
        val started = System.nanoTime()
        if (editor != null) editor.selectionModel.setSelection(0, editor.document.textLength)
        else check((component as SwingTerminal).selectAll())
        requestPaint()
        started
      }
      val elapsed = completed.await() - start
      withContext(Dispatchers.EDT) {
        operationPaint = null
        if (editor != null) {
          assertThat(editor.selectionModel.selectionEnd - editor.selectionModel.selectionStart).isEqualTo(editor.document.textLength)
          editor.selectionModel.removeSelection()
        }
        else {
          val terminal = component as SwingTerminal
          val range = requireNotNull(terminal.currentSelectionRange())
          nativeSession!!.terminalSession.readPublishedFrame { frame ->
            assertThat(range.anchorAbsoluteRow).isEqualTo(frame.discardedCount)
            assertThat(range.anchorColumn).isZero()
            assertThat(range.caretAbsoluteRow).isEqualTo(frame.discardedCount + frame.historySize + frame.rows - 1L)
            assertThat(range.caretColumn).isEqualTo(frame.columns)
          }
          terminal.clearSelection()
        }
      }
      return elapsed
    }

    suspend fun searchHistory(query: String): Long {
      val ready = CompletableDeferred<Long>()
      val expected = requireNotNull(expectedRetained)
      operationQueryOffset = expected.indexOf(query)
      check(operationQueryOffset >= 0 && operationQueryOffset == expected.lastIndexOf(query))
      val start = withContext(Dispatchers.EDT) {
        if (editor != null && editorSearch == null) {
          val model = FindModel().apply { isCaseSensitive = true; isRegularExpressions = false; isGlobal = true }
          editorSearch = TerminalSearchSession(project, editor, model) {}
          // Read the completion source once, outside timing. The production search and its highlighter remain unchanged.
          val field = TerminalSearchSession::class.java.getDeclaredField("searchResults").apply { isAccessible = true }
          val results = field.get(editorSearch) as SearchResults
          editorSearchResults = results
          results.addListener(object : SearchResults.SearchResultsListener {
            override fun cursorMoved() {}
            override fun searchResultsUpdated(sr: SearchResults) {
              if (!sr.isUpdating && sr.matchesCount == 1) searchReady?.complete(System.nanoTime())
            }
            override fun updateFinished() {
              if (results.matchesCount == 1) searchReady?.complete(System.nanoTime())
            }
          })
        }
        if (editor == null) (component as SwingTerminal).setSearchCaseSensitive(true)
        searchReady = ready
        val started = System.nanoTime()
        if (editor != null) editorSearch!!.findModel.stringToFind = query
        else (component as SwingTerminal).search(query)
        started
      }
      if (editor != null) ready.await()
      else {
        val state = (component as SwingTerminal).searchState.first { it.query == query && !it.isSearching }
        assertThat(state.failure).isNull()
        assertThat(state.resultCount).isEqualTo(1)
      }
      val painted = CompletableDeferred<Long>()
      withContext(Dispatchers.EDT) {
        if (editor != null) assertThat(editorSearchResults!!.matchesCount).isEqualTo(1)
        operationQuery = query
        operationPaint = painted
        requestPaint()
      }
      val elapsed = painted.await() - start
      withContext(Dispatchers.EDT) {
        assertThat(operationViewportReady()).isTrue()
        operationPaint = null
        operationQuery = null
        searchReady = null
        if (editor != null) {
          editorSearch!!.close()
          editorSearch = null
          editorSearchResults = null
        }
        else (component as SwingTerminal).clearSearch()
      }
      return elapsed
    }

    private fun operationViewportReady(): Boolean {
      val query = operationQuery ?: return true
      if (editor != null) {
        if (editorSearchResults?.getCursorVisualIndex() != 1) return false
        val top = editor.offsetToXY(operationQueryOffset).y
        val bottom = editor.offsetToXY(operationQueryOffset + query.length).y + editor.lineHeight
        val visible = editor.scrollingModel.visibleArea
        return top >= visible.y && bottom <= visible.y + visible.height
      }
      val viewport = (component as SwingTerminal).viewportState()
      return nativeSession!!.terminalSession.readPublishedFrame { frame ->
        if (frame.scrollbackOffset != viewport.renderOffset) return@readPublishedFrame false
        (0..frame.rows * frame.columns - query.length).any { start ->
          query.indices.all { frame.codeWords[start + it] == query[it].code }
        }
      } == true
    }

    private fun prepareViewport() {
      if (editor != null) {
        val preferred = component.preferredSize
        component.setSize(max(image.width, preferred.width), max(image.height, preferred.height))
        editor.scrollingModel.scrollVertically(max(0, preferred.height - image.height))
      }
    }

    private fun paint(clip: Rectangle? = null) {
      val offset = editor?.scrollingModel?.visibleArea?.y ?: 0
      if (clip == null) graphics.setClip(0, 0, image.width, image.height)
      else graphics.setClip(clip.x, clip.y, clip.width, clip.height)
      graphics.translate(0, -offset)
      try { component.paint(graphics) }
      finally { graphics.translate(0, offset) }
    }

    fun interactiveStateChanged() {
      val update = interactive ?: return
      if (update.completed.isCompleted) return
      if (update.expectedPixels != null) requestPaint()
      else if (cursorMatches(update)) update.finish(System.nanoTime())
    }

    private fun cursorMatches(update: InteractiveUpdate): Boolean {
      val native = nativeSession
      if (native != null) {
        return native.terminalSession.readPublishedFrame { it.cursorRow == update.row && it.cursorColumn == update.column } == true
      }
      val model = view.outputModels.regular
      val line = model.getLineByOffset(model.cursorOffset)
      return line - model.getLineByOffset(model.screenTopOffset) == update.row.toLong() &&
             model.cursorOffset - model.getStartOfLine(line) == update.column.toLong()
    }

    /** Copies the top half of the painted screen. The setup marker is below this region. */
    suspend fun interactiveReference(): IntArray = withContext(Dispatchers.EDT) {
      paintInteractiveScreen()
      pixels.copyOf(image.width * (image.height / 2))
    }

    private fun paintInteractiveScreen() {
      if (editor != null) {
        val model = view.outputModels.regular
        val top = (model.screenTopOffset - model.startOffset).toInt()
        val y = editor.offsetToXY(top).y
        if (editor.scrollingModel.visibleArea.y != y) editor.scrollingModel.scrollVertically(y)
      }
      paint()
    }

    suspend fun submitInteractive(payload: String, row: Int, column: Int, expectedPixels: IntArray? = null): InteractiveSample {
      val update = InteractiveUpdate(row, column, expectedPixels)
      try {
        withContext(Dispatchers.EDT) {
          if (expectedPixels == null) check(!cursorMatches(update))
          else {
            paintInteractiveScreen()
            check(!Arrays.equals(pixels, 0, expectedPixels.size, expectedPixels, 0, expectedPixels.size)) {
              "The expected image already matches the starting screen for $backend"
            }
          }
          interactive = update
          observer.active = this@PaintHarness
        }
        val allocated = allocationBean.totalThreadAllocatedBytes
        val start = System.nanoTime()
        connector.feed(payload)
        val nanos = update.completed.await() - start
        return InteractiveSample(nanos, update.allocatedAtCompletion - allocated)
      }
      finally {
        withContext(NonCancellable + Dispatchers.EDT) {
          if (!update.completed.isCompleted) {
            println("INTERACTIVE INCOMPLETE $backend row=$row column=$column matchingCursor=${cursorMatches(update)} " +
                    "viewport=${editor?.scrollingModel?.visibleArea}")
          }
          interactive = null
        }
      }
    }

    fun measurePaint() {
      val bean = allocationBean
      prepareViewport()
      val thread = Thread.currentThread().threadId()
      val bytesBefore = bean.getThreadAllocatedBytes(thread)
      check(bytesBefore >= 0) { "The JVM did not report allocations for the EDT" }
      val start = System.nanoTime()
      repeat(PAINTS_PER_SAMPLE) { paint() }
      paintNanos = (System.nanoTime() - start) / PAINTS_PER_SAMPLE
      paintBytes = (bean.getThreadAllocatedBytes(thread) - bytesBefore) / PAINTS_PER_SAMPLE
    }

    private fun containsColor(rgb: Int): Boolean {
      var matches = 0
      for (pixel in pixels) if (pixel == rgb && ++matches >= 128) return true
      return false
    }

    fun saveImage(mode: String, workload: String) {
      val directory = Path.of(System.getenv("TERMINAL_RENDER_IMAGE_DIR"))
      Files.createDirectories(directory)
      ImageIO.write(image, "png", directory.resolve("$mode-$backend-$workload.png").toFile())
    }

    suspend fun close() {
      withContext(Dispatchers.EDT) {
        closed = true
        probe = null
        partialPaint = null
        if (observer.active === this@PaintHarness) observer.active = null
        editorSearch?.close()
        realizedRoot?.removeNotify()
        realizedRoot = null
        graphics.dispose()
      }
      viewScope.coroutineContext.job.cancelAndJoin()
      scope.coroutineContext.job.cancel()
      connector.close()
      scope.coroutineContext.job.join()
    }
  }

  class Workload(
    val name: String,
    val payload: String,
    val prelude: String = "",
    val expectedTextBeforeMarker: () -> String = { textAfterErasingMarker(payload) },
  ) {
    override fun toString(): String = name
  }

  companion object {
    fun workloads(): List<Workload> {
      val cursor = buildString {
        repeat(CURSOR_WRITES) { append("\u001b[${it % ROWS + 1};${it % COLUMNS + 1}H*") }
        append("\u001b[$ROWS;1H")
      }
      val history = buildString {
        repeat(6_000) { append("history $it short line 0123456789\r\n") }
      }
      return listOf(
        Workload("plain-text-scroll", buildString {
          repeat(1_300) { append("The quick brown fox jumps over the lazy dog 0123456789 abcdefghijklmnopqrstuvwx\r\n") }
        }),
        Workload("log-file-cat", buildString {
          repeat(6_000) {
            append("2026-10-04 12:34:56 [request ").append(it).append("] INFO component: state=RUNNING payload=")
            append("abcdefghijklmnopqrstuvwxyz-0123456789 ".repeat(3)).append("\r\n")
          }
        }),
        Workload("single-long-line", "wrap-me-0123456789-abcdefghijklmnopqrstuvwxyz-".repeat(2_913)),
        Workload("sgr-colored-cells", buildString {
          repeat(6_000) {
            append("\u001b[38;5;${it % 256}mcell")
            if (it % 12 == 11) append("\r\n")
          }
          append("\u001b[0m\r\n")
        }) { "\n" + ("cell".repeat(12) + "\n").repeat(500) + "\n" },
        Workload(CURSOR_BURST_NAME, cursor) { cursorWorkloadText("") },
        Workload("unicode-wide", buildString {
          repeat(1_200) { append("你好世界 こんにちは 안녕하세요 😀🎉🚀 mixed-ascii-0123456789\r\n") }
        }),
        Workload("retained-history", cursor, history) { cursorWorkloadText(history) },
      )
    }

    private fun cursorWorkloadText(prelude: String): String {
      val lines = ("\n" + prelude.replace("\r\n", "\n") + "\n").split('\n')
      val firstVisible = maxOf(0, lines.size - ROWS)
      val screen = Array(ROWS) { row -> StringBuilder(lines.getOrNull(firstVisible + row).orEmpty()) }
      repeat(CURSOR_WRITES) { index ->
        val row = screen[index % ROWS]
        val column = index % COLUMNS
        while (row.length <= column) row.append(' ')
        row[column] = '*'
      }
      return buildString {
        for (line in 0 until firstVisible) append(lines[line]).append('\n')
        screen.joinTo(this, "\n")
      }
    }

    private const val CURSOR_WRITES = 10_000

    fun textAfterErasingMarker(payload: String): String = "\n" + payload.replace("\r\n", "\n")

    const val COLUMNS = 80
    const val ROWS = 24
    const val PAINTS_PER_SAMPLE = 25
    const val ERASE_MARKER = "\r\u001b[2K"
    const val CLEAR = "\u001b[0m\u001b[3J\u001b[2J\u001b[H"
    private val allocationBean = (ManagementFactory.getThreadMXBean() as ThreadMXBean).also {
      check(it.isThreadAllocatedMemorySupported) { "The JVM does not support thread allocation measurements" }
      it.isThreadAllocatedMemoryEnabled = true
    }
  }
}

/** Sets the component size after binding and verifies the session grid. */
internal fun configureKetraTermBenchmarkGrid(
  terminal: SwingTerminal,
  session: KetraTermTerminalSession,
  columns: Int,
  rows: Int,
) {
  terminal.size = terminal.preferredGridSize(columns, rows)
  terminal.dispatchEvent(ComponentEvent(terminal, ComponentEvent.COMPONENT_RESIZED))
  assertThat(terminal.visibleGridSize()).isEqualTo(Dimension(columns, rows))
  session.terminalSession.readRenderFrame { frame ->
    assertThat(frame.columns).isEqualTo(columns)
    assertThat(frame.rows).isEqualTo(rows)
  }
}

/** An explicit workload condition, never a completion or correctness barrier. */
internal val benchmarkPreInputIdleMillis: Long =
  (System.getProperty("ketraterm.benchmark.preInputIdleMillis")?.toLong() ?: 40L).also {
    require(it >= 0) { "preInputIdleMillis must be nonnegative" }
  }

internal suspend fun awaitBenchmarkInputGap() {
  if (benchmarkPreInputIdleMillis > 0) kotlinx.coroutines.delay(benchmarkPreInputIdleMillis)
}

internal fun <T> selectedBenchmarkBackends(available: List<T>, name: (T) -> String): List<T> {
  val requested = System.getProperty("ketraterm.benchmark.backends")?.split(',')?.map(String::trim) ?: return available
  val names = TerminalEmulatorType.entries.map { it.name }
  require(requested.all { it in names }) { "Unknown performance backend: $requested" }
  val selected = available.filter { name(it) in requested }
  require(selected.isNotEmpty()) { "This endpoint supports ${available.map(name)}. Requested: $requested" }
  return selected
}
