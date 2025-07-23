// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.ui

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.view.DoubleWidthCharacterStrategy
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.impl.zoomIndicator.ZoomIndicatorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.withCurrentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalColorPalette
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.DocumentUtil
import com.intellij.util.application
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.AwtTransformers
import com.jediterm.terminal.util.CharUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.output.TextAttributesProvider
import org.jetbrains.plugins.terminal.block.output.TextStyleAdapter
import org.jetbrains.plugins.terminal.block.session.TerminalModel
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext
import java.awt.geom.Dimension2D
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

@ApiStatus.Internal
object TerminalUiUtils {
  fun createOutputEditor(
    document: Document,
    project: Project,
    settings: JBTerminalSystemSettingsProviderBase,
    installContextMenu: Boolean,
  ): EditorImpl {
    // Terminal does not need Editor's Undo/Redo functionality.
    // So, it is better to disable it to not store the document changes in UndoManager cache.
    UndoUtil.disableUndoFor(document)

    val editor = EditorFactory.getInstance().createEditor(document, project, EditorKind.CONSOLE) as EditorImpl
    editor.isScrollToCaret = false
    editor.isRendererMode = true
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.gutterComponentEx.isPaintBackground = false
    editor.gutterComponentEx.setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.HIDE)

    // Editor installs its own drop target during `javax.swing.JComponent.setTransferHandler` call.
    // But it blocks the DnD installed in the terminal tool window: `org.jetbrains.plugins.terminal.TerminalToolWindowPanel.installDnD`.
    // So, let's remove it to enable the terminal DnD implementation.
    editor.contentComponent.dropTarget = null

    val terminalColorScheme = TerminalColorScheme()
    editor.colorsScheme = terminalColorScheme
    editor.putUserData(TerminalColorScheme.KEY, terminalColorScheme)
    val editorDisposable = Disposer.newDisposable()
    EditorUtil.disposeWithEditor(editor, editorDisposable)
    ApplicationManager.getApplication().messageBus.connect(editorDisposable).run {
      subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheme ->
        if (scheme != null) {
          terminalColorScheme.globalScheme = scheme
        }
      })
    }

    editor.settings.apply {
      isShowingSpecialChars = false
      isLineNumbersShown = false
      setGutterIconsShown(false)
      isRightMarginShown = false
      isFoldingOutlineShown = false
      isCaretRowShown = false
      additionalLinesCount = 0
      additionalColumnsCount = 0
      isAdditionalPageAtBottom = false
      isBlockCursor = true
      isWhitespacesShown = false
      isUseCustomSoftWrapIndent = false
    }

    editor.applyFontSettings(settings)

    val editorGrid = checkNotNull(editor.characterGrid) { "The editor did not switch into the grid mode" }

    editorGrid.doubleWidthCharacterStrategy = DoubleWidthCharacterStrategy {
      codePoint -> CharUtils.isDoubleWidthCharacter(codePoint, false)
    }

    if (installContextMenu) {
      installPopupMenu(editor)
    }
    return editor
  }

  private fun installPopupMenu(editor: EditorEx) {
    editor.installPopupHandler(object : ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent): ActionGroup = getPopupMenuGroup(editor, event)
    })
  }

  private fun getPopupMenuGroup(editor: EditorEx, event: EditorMouseEvent): ActionGroup {
    ThreadingAssertions.assertEventDispatchThread()
    val info: HyperlinkInfo? = EditorHyperlinkSupport.get(editor).getHyperlinkInfoByEvent(event)
    val customPopupMenuGroup = info.asSafely<HyperlinkWithPopupMenuInfo>()?.getPopupMenuGroup(event.mouseEvent)
    val defaultPopupMenuGroup = ActionManager.getInstance().getAction(TERMINAL_OUTPUT_CONTEXT_MENU) as ActionGroup
    return concatGroups(customPopupMenuGroup, defaultPopupMenuGroup)
  }

  private fun concatGroups(vararg groups: ActionGroup?): ActionGroup {
    val separatedGroups = groups.filterNotNull().flatMapIndexed { index, group ->
      if (index == 0) listOf(group) else listOf(Separator.create(), group)
    }
    // 1. Leading, trailing and duplicated separators are eliminated automatically (ActionUpdater.removeUnnecessarySeparators).
    //    This can be the case when a group has no visible actions.
    // 2. Whether a group's children are injected into the parent group or are shown as a submenu is controlled
    //    by `ActionGroup.isPopup`.
    return DefaultActionGroup(separatedGroups)
  }

  fun createSingleShortcutSet(@MagicConstant(flagsFromClass = KeyEvent::class) keyCode: Int,
                              @MagicConstant(flagsFromClass = InputEvent::class) modifiers: Int): ShortcutSet {
    val keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
    return CustomShortcutSet(keyStroke)
  }

  fun calculateTerminalSize(componentSize: Dimension, charSize: Dimension2D): TermSize {
    val width = componentSize.width / charSize.width
    val height = componentSize.height / charSize.height
    return ensureTermMinimumSize(TermSize(width.toInt(), height.toInt()))
  }

  @RequiresEdt
  fun getComponentSizeInitializedFuture(component: Component): CompletableFuture<*> {
    val size = component.size
    if (size.width > 0 || size.height > 0) {
      return CompletableFuture.completedFuture(Unit)
    }
    if (!UIUtil.isShowing(component, false)) {
      return CompletableFuture.failedFuture<Unit>(IllegalStateException("component should be showing"))
    }
    val componentResizedFuture = CompletableFuture<Unit>()
    val resizedListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        componentResizedFuture.complete(Unit)
      }
    }
    component.addComponentListener(resizedListener)
    componentResizedFuture.whenComplete { _, _ ->
      component.removeComponentListener(resizedListener)
    }
    return componentResizedFuture
  }

  fun cancelFutureByTimeout(future: CompletableFuture<*>, timeoutMillis: Long, parentDisposable: Disposable) {
    val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    val request = Runnable {
      future.completeExceptionally(IllegalStateException("Terminal component layout is timed out (>${timeoutMillis}ms)"))
    }
    alarm.addRequest(request, timeoutMillis, ModalityState.any())

    Disposer.register(alarm) {
      if (!future.isDone) {
        invokeLater(ModalityState.any()) {
          future.completeExceptionally(IllegalStateException("parent disposed"))
        }
      }
    }
    future.whenComplete { _, _ ->
      Disposer.dispose(alarm)
    }
  }

  fun toFloatAndScale(value: Int): Float = JBUIScale.scale(value.toFloat())

  @ApiStatus.Internal
  fun TextStyle.toTextAttributes(palette: TerminalColorPalette): TextAttributes {
    return TextAttributes().also { attr ->
      // [TerminalColorPalette.getDefaultBackground] is not applied to [TextAttributes].
      // It's passed to [EditorEx.setBackgroundColor] / [JComponent.setBackground] to
      // paint the background uniformly.
      attr.backgroundColor = getEffectiveBackgroundNoDefault(this, palette)
      attr.foregroundColor = getResultForeground(this, palette)
      if (hasOption(TextStyle.Option.BOLD)) {
        attr.fontType = attr.fontType or Font.BOLD
      }
      if (hasOption(TextStyle.Option.ITALIC)) {
        attr.fontType = attr.fontType or Font.ITALIC
      }
      if (hasOption(TextStyle.Option.UNDERLINED)) {
        attr.withAdditionalEffect(EffectType.LINE_UNDERSCORE, attr.foregroundColor)
      }
    }
  }

  fun TerminalColorPalette.getAwtForegroundByIndex(colorIndex: Int): Color {
    val color = if (colorIndex in 0..15) {
      getForeground(TerminalColor.index(colorIndex))
    }
    else defaultForeground
    return AwtTransformers.toAwtColor(color)!!
  }

  private fun getResultForeground(style: TextStyle, palette: TerminalColorPalette): Color {
    val foreground = getEffectiveForegroundOrDefault(style, palette)
    return if (style.hasOption(TextStyle.Option.DIM)) {
      val background = getEffectiveBackgroundOrDefault(style, palette)
      @Suppress("UseJBColor")
      Color((foreground.red + background.red) / 2,
            (foreground.green + background.green) / 2,
            (foreground.blue + background.blue) / 2,
            foreground.alpha)
    }
    else foreground
  }

  private fun getEffectiveForegroundOrDefault(style: TextStyle, palette: TerminalColorPalette): Color {
    return if (style.hasOption(TextStyle.Option.INVERSE)) toBackground(style, palette) else toForeground(style, palette)
  }

  private fun getEffectiveBackgroundOrDefault(style: TextStyle, palette: TerminalColorPalette): Color {
    return if (style.hasOption(TextStyle.Option.INVERSE)) toForeground(style, palette) else toBackground(style, palette)
  }

  private fun getEffectiveBackgroundNoDefault(style: TextStyle, palette: TerminalColorPalette): Color? {
    return if (style.hasOption(TextStyle.Option.INVERSE)) {
      toForeground(style, palette)
    }
    else {
      style.background?.let { AwtTransformers.toAwtColor(palette.getBackground(it)) }
    }
  }

  private fun toForeground(style: TextStyle, palette: TerminalColorPalette): Color {
    val color = style.foreground?.let { palette.getForeground(it) } ?: palette.defaultForeground
    return AwtTransformers.toAwtColor(color)!!
  }

  private fun toBackground(style: TextStyle, palette: TerminalColorPalette): Color {
    val color = style.background?.let { palette.getBackground(it) } ?: palette.defaultBackground
    return AwtTransformers.toAwtColor(color)!!
  }

  fun plainAttributesProvider(foregroundColorIndex: Int, palette: TerminalColorPalette): TextAttributesProvider {
    return TextStyleAdapter(TextStyle(TerminalColor(foregroundColorIndex), null), palette)
  }

  const val NEW_TERMINAL_OUTPUT_CAPACITY_KB: String = "new.terminal.output.capacity.kb"

  fun getDefaultMaxOutputLength(): Int {
    return AdvancedSettings.getInt(NEW_TERMINAL_OUTPUT_CAPACITY_KB).coerceIn(1, 10 * 1024) * 1024
  }

  private const val TERMINAL_OUTPUT_CONTEXT_MENU = "Terminal.OutputContextMenu"

  const val GREEN_COLOR_INDEX: Int = 2
  const val YELLOW_COLOR_INDEX: Int = 3
}

fun EditorImpl.applyFontSettings(newSettings: JBTerminalSystemSettingsProviderBase) {
  val colorScheme = checkNotNull(getUserData(TerminalColorScheme.KEY)) { "Should've been set on creation" }
  colorScheme.fontPreferences = newSettings.fontPreferences
  // for some reason, even though fontPreferences contains lineSpacing, the editor doesn't take it from there
  colorScheme.lineSpacing = newSettings.lineSpacing
  settings.apply {
    characterGridWidthMultiplier = newSettings.columnSpacing
  }
  // The font size in the preferences is not scaled.
  // Global user scaling will be applied by the editor itself,
  // but if the _terminal_ font size was changed temporarily (Ctrl/Cmd+wheel, pinch zoom, etc.),
  // it needs to be applied explicitly to the new editor.
  setTerminalFontSize(newSettings.terminalFontSize, showZoomIndicator = false)
}

@ApiStatus.Internal
fun EditorImpl.setTerminalFontSize(
  fontSize: Float,
  showZoomIndicator: Boolean,
) {
  if (!showZoomIndicator) {
    putUserData(ZoomIndicatorManager.SUPPRESS_ZOOM_INDICATOR_ONCE, true)
  }
  setFontSize(
    fontSize,
    ChangeTerminalFontSizeStrategy.preferredZoomPointRelative(this),
    true
  )
}

internal fun Editor.getCharSize(): Dimension2D {
  val baseContext = FontInfo.getFontRenderContext(contentComponent)
  val context = FontRenderContext(baseContext.transform,
                                  AntialiasingType.getKeyForCurrentScope(true),
                                  UISettings.editorFractionalMetricsHint)
  val fontMetrics = FontInfo.getFontMetrics(colorsScheme.getFont(EditorFontType.PLAIN), context)
  // Using the '%' to calculate the size as it's usually one of the widest non-double-width characters.
  // For monospaced fonts this shouldn't really matter, but let's stay on the safe side.
  // Otherwise, we may end up with some characters falsely displayed as double-width ones.
  val width = FontLayoutService.getInstance().charWidth2D(fontMetrics, '%'.code)
  val columnSpacing = settings.characterGridWidthMultiplier ?: 1.0f
  // lineHeight already includes lineSpacing
  return Dimension2DDouble(width.toDouble() * columnSpacing, lineHeight.toDouble())
}

fun Editor.calculateTerminalSize(): TermSize? {
  val grid = (this as? EditorImpl)?.characterGrid ?: return null
  return if (grid.rows > 0 && grid.columns > 0) {
    ensureTermMinimumSize(TermSize(grid.columns, grid.rows))
  } else {
    null
  }
}

private fun ensureTermMinimumSize(size: TermSize): TermSize {
  return TermSize(max(TerminalModel.MIN_WIDTH, size.columns), max(TerminalModel.MIN_HEIGHT, size.rows))
}

private class Dimension2DDouble(private var width: Double, private var height: Double) : Dimension2D() {
  override fun getWidth(): Double = width

  override fun getHeight(): Double = height

  override fun setSize(width: Double, height: Double) {
    this.width = width
    this.height = height
  }
}

@RequiresBlockingContext
internal fun invokeLater(expired: (() -> Boolean)? = null,
                modalityState: ModalityState = ModalityState.defaultModalityState(),
                runnable: Runnable) {
  if (expired != null) {
    ApplicationManager.getApplication().invokeLater(runnable, modalityState) {
      expired()
    }
  }
  else {
    ApplicationManager.getApplication().invokeLater(runnable, modalityState)
  }
}

@RequiresBlockingContext
internal fun invokeLaterIfNeeded(expired: (() -> Boolean)? = null,
                                 modalityState: ModalityState = ModalityState.defaultModalityState(),
                                 runnable: Runnable) {
  if (ApplicationManager.getApplication().isDispatchThread) {
    runnable.run()
  }
  else {
    invokeLater(expired, modalityState, runnable)
  }
}

internal fun Editor.getDisposed(): () -> Boolean = { this.isDisposed }

internal inline fun <reified T> Document.executeInBulk(crossinline block: () -> T): T {
  var result: T? = null
  DocumentUtil.executeInBulk(this) {
    result = block()
  }
  return result!!
}

private val TERMINAL_OUTPUT_SCROLL_CHANGING_ACTION_KEY = Key.create<Unit>("TERMINAL_EDITOR_SIZE_CHANGING_ACTION")

/**
 * Indicates that action that may modify scroll offset or editor size is in progress.
 * It should be used only to indicate internal programmatic actions that are not explicitly caused by the user interaction.
 * For example, terminal output text update, or adding inlays to create insets between command blocks.
 */
@get:ApiStatus.Internal
@set:ApiStatus.Internal
var Editor.isTerminalOutputScrollChangingActionInProgress: Boolean
  get() = getUserData(TERMINAL_OUTPUT_SCROLL_CHANGING_ACTION_KEY) != null
  set(value) = putUserData(TERMINAL_OUTPUT_SCROLL_CHANGING_ACTION_KEY, if (value) Unit else null)

@ApiStatus.Internal
inline fun <T> Editor.doTerminalOutputScrollChangingAction(action: () -> T): T {
  isTerminalOutputScrollChangingActionInProgress = true
  try {
    return action()
  }
  finally {
    isTerminalOutputScrollChangingActionInProgress = false
  }
}

/**
 * Scroll to bottom if we were at the bottom before executing the [action]
 */
@RequiresEdt
internal inline fun <T> Editor.doWithScrollingAware(action: () -> T): T {
  val wasAtBottom = scrollingModel.visibleArea.let { it.y + it.height } == contentComponent.height
  try {
    return action()
  }
  finally {
    if (wasAtBottom) {
      scrollToBottom()
    }
  }
}

@ApiStatus.Internal
@RequiresEdt
inline fun <T> Editor.doWithoutScrollingAnimation(action: () -> T): T {
  scrollingModel.disableAnimation()
  return try {
    action()
  }
  finally {
    scrollingModel.enableAnimation()
  }
}

@RequiresEdt
internal fun Editor.scrollToBottom() {
  // disable animation to perform scrolling atomically
  doWithoutScrollingAnimation {
    val visibleArea = scrollingModel.visibleArea
    scrollingModel.scrollVertically(contentComponent.height - visibleArea.height)
  }
}

internal fun stickScrollBarToBottom(verticalScrollBar: JScrollBar) {
  verticalScrollBar.model.addChangeListener(object : ChangeListener {
    var preventRecursion: Boolean = false
    var prevValue: Int = 0
    var prevMaximum: Int = 0
    var prevExtent: Int = 0

    override fun stateChanged(e: ChangeEvent?) {
      if (preventRecursion) return

      val model = verticalScrollBar.model
      val maximum = model.maximum
      val extent = model.extent

      if (extent != prevExtent || maximum != prevMaximum) {
        // stay at the bottom if the previous position was at the bottom
        if (prevValue == prevMaximum - prevExtent) {
          preventRecursion = true
          try {
            model.value = maximum - extent
          }
          finally {
            preventRecursion = false
          }
        }
      }

      prevValue = model.value
      prevMaximum = model.maximum
      prevExtent = model.extent
    }
  })
}

/** @return the string without second part of double width character if any */
internal fun CharBuffer.normalize(): String {
  val s = this.toString()
  return if (s.contains(CharUtils.DWC)) s.filterTo(StringBuilder(s.length - 1)) { it != CharUtils.DWC }.toString() else s
}

@ApiStatus.Internal
fun TerminalLine.getLengthWithoutDwc(): Int {
  val dwcCount = entries.fold(0) { curCount, entry ->
    val dwcInEntryCount = entry.text.count { it == CharUtils.DWC }
    curCount + dwcInEntryCount
  }
  return length() - dwcCount
}

inline fun <T> TerminalTextBuffer.withLock(callable: (TerminalTextBuffer) -> T): T {
  lock()
  return try {
    callable(this)
  }
  finally {
    unlock()
  }
}

fun JBLayeredPane.addToLayer(component: JComponent, layer: Int) {
  add(component, layer as Any) // Any is needed to resolve to the correct overload.
}

@ApiStatus.Internal
fun getClipboardText(useSystemSelectionClipboardIfAvailable: Boolean = false): String? {
  if (useSystemSelectionClipboardIfAvailable) {
    val text = getTextContent(CopyPasteManager.getInstance().systemSelectionContents)
    if (text != null) {
      return text
    }
  }
  return getTextContent(CopyPasteManager.getInstance().contents)
}

private fun getTextContent(content: Transferable?): String? {
  if (content == null) return null

  return try {
    if (content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      content.getTransferData(DataFlavor.stringFlavor) as String
    }
    else null
  }
  catch (t: Throwable) {
    logger<TerminalUiUtils>().error("Failed to get text from clipboard", t)
    return null
  }
}

/**
 * The following logic was borrowed from JediTerm.
 * Sanitize clipboard text to use CR as the line separator.
 * See https://github.com/JetBrains/jediterm/issues/136.
 */
@ApiStatus.Internal
fun sanitizeLineSeparators(text: String): String {
  // On Windows, Java automatically does this CRLF->LF sanitization, but
  // other terminals on Unix typically also do this sanitization.
  var t = text
  if (!SystemInfoRt.isWindows) {
    t = text.replace("\r\n", "\n")
  }
  // Now convert this into what the terminal typically expects.
  return t.replace("\n", "\r")
}

/**
 * Should be used when you need to change the frontend terminal settings that are synced with the backend.
 * See [org.jetbrains.plugins.terminal.TerminalRemoteSettingsInfoProvider].
 * It prohibits simultaneous update of the setting on both frontend and backend by executing it only on the frontend.
 * And then launches sending the updates to the backend in the provided [coroutineScope].
 */
internal fun updateFrontendSettingsAndSync(coroutineScope: CoroutineScope, doUpdate: () -> Unit) {
  // Update the settings only on the IDE Frontend (or monolith).
  if (AppMode.isRemoteDevHost()) return

  try {
    doUpdate()
  }
  finally {
    // Trigger sending the updated values to the backend
    coroutineScope.launch {
      withCurrentThreadCoroutineScope {
        saveSettingsForRemoteDevelopment(application)
      }
    }
  }
}