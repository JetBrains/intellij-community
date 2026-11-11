// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// The overlay has the same colours on every theme: white text, a black outline, and the colour that the test gives.
@file:Suppress("UseJBColor")

package com.jetbrains.performancePlugin.remotedriver

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.TestOnly
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val TOP_OFFSET = 60
private const val MAX_WIDTH = 600
private const val SIDE_MARGIN = 16
private const val HORIZONTAL_PADDING = 8
private const val VERTICAL_PADDING = 4

/** 50% opacity, so the content under the overlay stays visible. */
private const val BACKGROUND_ALPHA = 0x80

/** The text is this much larger than the label font, in points. */
private const val FONT_INCREASE = 2f

/** The time of a step shows in whole intervals. */
private val TIME_INTERVAL: Duration = 3.seconds

/**
 * Paints the text overlay of a Driver test at one place on the screen: the top of the main IDE window.
 * A window over that place, such as a dialog, paints its part of the overlay too, so the overlay stays visible.
 * The overlay is a painter of the IDE glass pane, so the mouse events and the component search ignore it.
 * A call returns at once, so a busy EDT does not stop the test. The EDT paints only the newest request.
 */
@TestOnly
@Suppress("HardCodedStringLiteral") // The overlay shows the text of a test, not a localized UI text.
object DriverOverlay {
  private val LOG = logger<DriverOverlay>()

  /** The request for a hide. */
  private val HIDE = Request("", null, 0, null)

  /** The newest request that the EDT did not apply yet. A newer request replaces it, so a fast series paints once. */
  private val pending = AtomicReference<Request?>()

  private var request: Request? = null
  private var shown: ShownOverlay? = null

  /** Updates the time of the step. */
  private val timer = Timer(0) { updateTime() }.apply { isRepeats = false }

  private val listenedWindows = HashSet<Window>()

  /** A window that moves or changes its size can cover the overlay, or stop to cover it. */
  private val windowMover = object : ComponentAdapter() {
    override fun componentMoved(e: ComponentEvent) = layoutOverlay()
    override fun componentResized(e: ComponentEvent) = layoutOverlay()
    override fun componentShown(e: ComponentEvent) = layoutOverlay()
    override fun componentHidden(e: ComponentEvent) = layoutOverlay()
  }

  /** A window that opens or closes can cover the overlay, or stop to cover it. */
  private val windowListener = AWTEventListener { event ->
    if (event.id == WindowEvent.WINDOW_OPENED) listen(event.source as Window)
    layoutOverlay()
  }

  /** A headless IDE and the backend of the split mode show no overlay. */
  private val isSupported: Boolean = !GraphicsEnvironment.isHeadless() && !AppMode.isHeadless() && !AppMode.isRemoteDevHost()

  /**
   * Shows [title] and an optional [message] on [rgb], and replaces the previous overlay.
   * With a non-negative [elapsedMillis], the title ends with the time of the step, for example `[6s]`, after the first interval.
   */
  @JvmStatic
  fun show(title: String, message: String?, rgb: Int, elapsedMillis: Long) {
    if (!isSupported) return
    val startedAtNanos = if (elapsedMillis >= 0) System.nanoTime() - elapsedMillis * 1_000_000 else null
    submit(Request(title, message, rgb, startedAtNanos))
  }

  @JvmStatic
  fun hide() {
    submit(HIDE)
  }

  @JvmStatic
  @RequiresEdt
  fun getShownTitle(): String? {
    applyPending()
    return shown?.title
  }

  @JvmStatic
  @RequiresEdt
  fun getShownMessage(): String? {
    applyPending()
    return shown?.message
  }

  /** The bounds of the shown overlay on the screen, or null when the IDE shows no overlay. */
  @JvmStatic
  @RequiresEdt
  fun getShownBounds(): Rectangle? {
    applyPending()
    return shown?.screenBounds()
  }

  private fun submit(request: Request) {
    // Only the first request of a series schedules the EDT. The later requests replace it before the EDT runs.
    if (pending.getAndSet(request) == null) {
      ApplicationManager.getApplication().invokeLater(::applyPending, ModalityState.any())
    }
  }

  @RequiresEdt
  private fun applyPending() {
    val next = pending.getAndSet(null) ?: return
    request = next.takeIf { it !== HIDE }
    paintRequest()
  }

  /** Paints [request] in a new overlay. */
  @RequiresEdt
  private fun paintRequest() {
    timer.stop()
    removeOverlay()
    val request = request ?: return
    paintSafely(request) {
      val main = findMainRootPane()
      val maxWidth = columnWidth(main)
      check(maxWidth > 2 * HORIZONTAL_PADDING) { "The window is too narrow for the overlay" }
      // A fixed left edge keeps the text in one place when the next overlay has another length.
      val overlay = ShownOverlay(main, Point((main.width - maxWidth) / 2, TOP_OFFSET))
      // The overlay shows before its first layout, so the removal also removes the parts of a failed layout.
      shown = overlay
      Toolkit.getDefaultToolkit().addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK)
      Window.getWindows().filter { it.isShowing }.forEach(::listen)
      overlay.setText(request.timedTitle(), request.message, createText(request, main))
    }
  }

  /** Replaces only the text of the overlay when the time of the step changes. */
  @RequiresEdt
  private fun updateTime() {
    val request = request ?: return
    val overlay = shown ?: return paintRequest()
    paintSafely(request) { overlay.setText(request.timedTitle(), request.message, createText(request, overlay.main)) }
  }

  /** Runs [paint], and schedules the next change of the time of the step. Logs a failure. */
  private inline fun paintSafely(request: Request, paint: () -> Unit) {
    try {
      paint()
      val nextTimeChange = request.nextTimeChange() ?: return
      timer.initialDelay = nextTimeChange.inWholeMilliseconds.toInt() + 1
      timer.start()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("The Driver overlay '${request.title}' failed", e)
    }
  }

  private fun createText(request: Request, main: JRootPane): OverlayText {
    val maxWidth = columnWidth(main)
    val title = request.timedTitle()
    val text = OverlayText(createLabel(title, request.message, Color.WHITE, maxWidth),
                           createLabel(title, request.message, Color.BLACK, maxWidth),
                           Color((BACKGROUND_ALPHA shl 24) or request.rgb, true))
    check(TOP_OFFSET + text.size.height <= main.height) { "The window is too low for the overlay" }
    return text
  }

  private fun removeOverlay() {
    val overlay = shown ?: return
    shown = null
    Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener)
    listenedWindows.forEach { it.removeComponentListener(windowMover) }
    listenedWindows.clear()
    overlay.removeParts()
  }

  private fun listen(window: Window) {
    if (listenedWindows.add(window)) window.addComponentListener(windowMover)
  }

  private fun layoutOverlay() {
    shown?.layout()
  }

  /** The IDE frame or the Welcome screen, or before they open, the active IDE window or any visible window. */
  private fun findMainRootPane(): JRootPane {
    val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    val windows = Frame.getFrames().filterIsInstance<JFrame>().asSequence() + sequenceOf(active) + Window.getWindows().asSequence()
    return windows.firstNotNullOfOrNull { it?.withIdeGlassPane() } ?: error("No visible IDE window with the IDE glass pane")
  }

  private fun columnWidth(rootPane: JRootPane): Int = minOf(MAX_WIDTH, rootPane.width - 2 * SIDE_MARGIN)

  private fun createLabel(title: String, message: String?, foreground: Color, maxWidth: Int): JLabel {
    val html = "<b>${StringUtil.escapeXmlEntities(title)}</b>" + (message?.let { "<br>${StringUtil.escapeXmlEntities(it)}" } ?: "")
    val label = JLabel("<html>$html</html>")
    label.font = JBFont.label().biggerOn(FONT_INCREASE)
    label.foreground = foreground
    label.border = BorderFactory.createEmptyBorder(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING)
    val size = label.preferredSize
    if (size.width <= maxWidth) {
      label.setSize(size.width, size.height)
      return label
    }
    // A fixed width makes the HTML view wrap the text. The label gives the view its size again when it paints.
    val view = label.getClientProperty(BasicHTML.propertyKey) as View
    view.setSize((maxWidth - 2 * HORIZONTAL_PADDING).toFloat(), 0f)
    label.setSize(maxWidth, view.getPreferredSpan(View.Y_AXIS).toInt() + 2 * VERTICAL_PADDING)
    return label
  }
}

/** A request of the client. [startedAtNanos] is the start of the step, as [System.nanoTime], or null for an overlay without a time. */
private class Request(val title: String, val message: String?, val rgb: Int, val startedAtNanos: Long?) {
  /** The title with the time of the step, for example `Step [6s]`, after the first interval. */
  fun timedTitle(): String {
    val intervals = intervals() ?: return title
    return if (intervals > 0) "$title [${TIME_INTERVAL * intervals}]" else title
  }

  /** The time until the time of the step changes, or null without a time. */
  fun nextTimeChange(): Duration? {
    val intervals = intervals() ?: return null
    return TIME_INTERVAL * (intervals + 1) - elapsed()
  }

  private fun intervals(): Int? = if (startedAtNanos == null) null else (elapsed() / TIME_INTERVAL).toInt()

  private fun elapsed(): Duration = (System.nanoTime() - (startedAtNanos ?: 0)).nanoseconds
}

private fun Window.withIdeGlassPane(): JRootPane? =
  SwingUtilities.getRootPane(this)?.takeIf { isShowing && it.isShowing && it.glassPane is IdeGlassPane }

/** The overlay at [location] of the [main] window. Each IDE window over that place on the screen paints a part of it. */
private class ShownOverlay(val main: JRootPane, private val location: Point) {
  var title: String? = null
    private set
  var message: String? = null
    private set
  private var text: OverlayText? = null
  private val parts = LinkedHashMap<JRootPane, OverlayPart>()

  fun setText(title: String, message: String?, text: OverlayText) {
    this.title = title
    this.message = message
    this.text = text
    parts.values.forEach { it.setText(text) }
    layout()
  }

  /** The bounds of the overlay on the screen, or null before the overlay has a text. */
  fun screenBounds(): Rectangle? {
    val text = text ?: return null
    val screenLocation = Point(location)
    SwingUtilities.convertPointToScreen(screenLocation, main.glassPane)
    return Rectangle(screenLocation, text.size)
  }

  /** Adds a part to each window over the overlay, moves the parts of the windows that moved, and removes the other parts. */
  fun layout() {
    val text = text ?: return
    val onScreen = screenBounds() ?: return
    val windows = Window.getWindows().mapNotNull { it.withIdeGlassPane() }.filter { rootPane ->
      rootPane === main || Rectangle(rootPane.locationOnScreen, rootPane.size).intersects(onScreen)
    }
    for (rootPane in parts.keys - windows.toSet()) {
      parts.remove(rootPane)?.remove()
    }
    for (rootPane in windows) {
      // The painter paints in the coordinates of the glass pane. Under a custom title bar, it starts below the root pane.
      val partLocation = onScreen.location
      SwingUtilities.convertPointFromScreen(partLocation, rootPane.glassPane)
      val bounds = Rectangle(partLocation, text.size)
      parts[rootPane]?.move(bounds) ?: parts.put(rootPane, OverlayPart(rootPane, text, bounds))
    }
  }

  fun removeParts() {
    parts.values.forEach { it.remove() }
    parts.clear()
  }
}

/** The white text, the same text in black for its outline, and the translucent background. */
private class OverlayText(val label: JLabel, val outline: JLabel, val background: Color) {
  val size: Dimension get() = label.size
}

/** The painter of the overlay on the glass pane of one window. */
private class OverlayPart(rootPane: JRootPane, text: OverlayText, bounds: Rectangle) {
  private val glassPane = rootPane.glassPane as JComponent
  private val painter = OverlayPainter(text, bounds)
  private val disposable = Disposer.newDisposable("Driver overlay")

  init {
    (glassPane as IdeGlassPane).addPainter(null, painter, disposable)
    // Paint now, so a screenshot right after this call shows the overlay.
    glassPane.paintImmediately(bounds)
  }

  fun setText(text: OverlayText) {
    painter.text = text
    glassPane.repaint(painter.bounds)
  }

  fun move(bounds: Rectangle) {
    if (bounds == painter.bounds) return
    glassPane.repaint(painter.bounds)
    painter.bounds = bounds
    glassPane.repaint(bounds)
  }

  fun remove() {
    // The removal of a painter does not repaint the glass pane.
    painter.removed = true
    Disposer.dispose(disposable)
    // Paint now, so a screen read right after this call, for example of a pixel colour, sees no overlay.
    glassPane.paintImmediately(painter.bounds)
  }
}

private class OverlayPainter(text: OverlayText, var bounds: Rectangle) : AbstractPainter() {
  @Volatile
  var removed: Boolean = false

  /** The glass pane repaints often, so the painter renders the text once. */
  private var image: BufferedImage? = null

  var text: OverlayText = text
    set(value) {
      field = value
      image = null
    }

  override fun needsRepaint(): Boolean = !removed

  override fun executePaint(component: Component, g: Graphics2D) {
    if (removed) return
    val image = image ?: render(g).also { image = it }
    g.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null)
  }

  /** Paints the translucent background, then the text with a dark outline, so the text is readable on any content. */
  private fun render(g: Graphics2D): BufferedImage {
    // The image has the device scale of the screen, so the text is sharp with HiDPI.
    val scale = g.transform.scaleX.coerceAtLeast(1.0)
    // A plain image keeps one explicit scale. A HiDPI image of UIUtil.createImage scales again in drawImage.
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage((bounds.width * scale).toInt(), (bounds.height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.scale(scale, scale)
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      graphics.color = text.background
      graphics.fillRect(0, 0, bounds.width, bounds.height)
      for (dx in -1..1) {
        for (dy in -1..1) {
          if (dx == 0 && dy == 0) continue
          val shifted = graphics.create(dx, dy, bounds.width, bounds.height)
          text.outline.paint(shifted)
          shifted.dispose()
        }
      }
      text.label.paint(graphics)
    }
    finally {
      graphics.dispose()
    }
    return image
  }
}
