// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.*
import com.jediterm.terminal.emulator.charset.CharacterSet
import com.jediterm.terminal.emulator.charset.GraphicSetState
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.StoredCursor
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.Tabulator
import com.jediterm.terminal.model.hyperlinks.LinkInfo
import com.jediterm.terminal.util.CharUtils
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * Mostly duplicates the logic of [com.jediterm.terminal.model.JediTerminal],
 * but do not modify the view directly.
 */
class TerminalController(private val model: TerminalModel,
                         private val settings: JBTerminalSystemSettingsProviderBase) : Terminal {
  private val tabulator: Tabulator = DefaultTabulator(model.width)
  private val terminalKeyEncoder: TerminalKeyEncoder = TerminalKeyEncoder()
  private val terminalModes: EnumSet<TerminalMode> = EnumSet.noneOf(TerminalMode::class.java)
  private val graphicSetState: GraphicSetState = GraphicSetState()
  private val windowTitlesStack = Stack<String>()

  private var cursorYChanged: Boolean = false
  private var scrollRegionTop: Int = 1
  private var scrollRegionBottom: Int = model.height
  private var storedCursor: StoredCursor? = null

  init {
    reset()
  }

  override fun resize(newTermSize: TermSize, origin: RequestOrigin) {
    resize(newTermSize, origin, CompletableFuture.completedFuture(null))
  }

  override fun resize(newTermSize: TermSize, origin: RequestOrigin, promptUpdated: CompletableFuture<*>) {
    val newSize = Dimension(newTermSize.columns, newTermSize.rows)
    ensureTermMinimumSize(newSize)
    if (model.width == newSize.width && model.height == newSize.height) {
      return
    }
    val oldHeight = model.height
    if (newSize.width == model.width) {
      doResize(newSize, origin, oldHeight)
    }
    else promptUpdated.thenRun { doResize(newSize, origin, oldHeight) }
  }

  private fun doResize(newTermSize: Dimension, origin: RequestOrigin, oldHeight: Int) {
    // TODO: Take selection into account
    model.resize(newTermSize, origin, model.cursorX, model.cursorY, null) { _, _, cursorX, cursorY ->
      model.setCursor(min(cursorX, model.width - 1), cursorY)
      tabulator.resize(model.width)
    }
    scrollRegionBottom += model.height - oldHeight
  }

  private fun ensureTermMinimumSize(termSize: Dimension) {
    termSize.setSize(max(TerminalModel.MIN_WIDTH, termSize.width), max(TerminalModel.MIN_HEIGHT, termSize.height))
  }

  override fun beep() {
    if (model.isCommandRunning && settings.audibleBell()) {
      Toolkit.getDefaultToolkit().beep()
    }
  }

  override fun backspace() {
    var cursorX = model.cursorX
    var cursorY = model.cursorY
    cursorX--
    if (cursorX < 0) {
      cursorY--
      cursorX = model.width - 1
    }
    cursorX = adjustX(cursorX, cursorY, -1)
    model.setCursor(cursorX, cursorY)
  }

  private fun adjustX(cursorX: Int, cursorY: Int, dx: Int): Int {
    return if (cursorY > -model.historyLinesCount
               && Character.isLowSurrogate(model.charAt(cursorX, cursorY - 1))) {
      // we don't want to place cursor on the second part of surrogate pair
      if (dx > 0) { // so we move it into the predefined direction
        if (cursorX == model.width) { //if it is the last in the line we return where we were
          cursorX - 1
        }
        else cursorX + 1
      }
      else cursorX - 1  //low surrogate character can't be the first character in the line
    }
    else cursorX
  }

  override fun horizontalTab() {
    if (model.cursorX >= model.width) {
      return
    }
    val length: Int = model.getLine(model.cursorY - 1).text.length
    val stop: Int = tabulator.nextTab(model.cursorX)
    var newCursorX = max(model.cursorX, length)
    if (newCursorX < stop) {
      val chars = CharArray(stop - newCursorX) { CharUtils.EMPTY_CHAR }
      writeDecodedCharacters(chars)
    }
    else {
      newCursorX = stop
    }
    newCursorX = adjustX(newCursorX, model.cursorY, 1)
    model.setCursor(newCursorX, model.cursorY)
  }

  override fun carriageReturn() {
    model.setCursor(0, model.cursorY)
  }

  override fun newLine() {
    cursorYChanged = true
    val newCursorY = scrollY(model.cursorY + 1)
    if (isAutoNewLine()) {
      carriageReturn()
    }
    model.setCursor(model.cursorX, newCursorY)
  }

  private fun scrollY(cursorY: Int): Int {
    return model.withContentLock {
      if (cursorY > scrollRegionBottom) {
        model.scrollArea(scrollRegionTop, scrollRegionBottom, scrollRegionBottom - cursorY)
        scrollRegionBottom
      }
      else if (cursorY < scrollRegionTop) {
        scrollRegionTop
      }
      else cursorY
    }
  }

  override fun mapCharsetToGL(num: Int) {
    graphicSetState.setGL(num)
  }

  override fun mapCharsetToGR(num: Int) {
    graphicSetState.setGR(num)
  }

  override fun designateCharacterSet(tableNumber: Int, charset: Char) {
    val gs = graphicSetState.getGraphicSet(tableNumber)
    graphicSetState.designateGraphicSet(gs, charset)
  }

  override fun setAnsiConformanceLevel(level: Int) {
    when (level) {
      1, 2 -> {
        graphicSetState.designateGraphicSet(0, CharacterSet.ASCII) //ASCII designated as G0
        graphicSetState.designateGraphicSet(1,
                                            CharacterSet.DEC_SUPPLEMENTAL) //TODO: not DEC supplemental, but ISO Latin-1 supplemental designated as G1
        mapCharsetToGL(0)
        mapCharsetToGR(1)
      }
      3 -> {
        designateCharacterSet(0, 'B') //ASCII designated as G0
        mapCharsetToGL(0)
      }
      else -> {
        throw IllegalArgumentException()
      }
    }
  }

  override fun writeDoubleByte(bytesOfChar: CharArray) {
    writeCharacters(String(bytesOfChar, 0, 2))
  }

  override fun writeCharacters(string: String) {
    val decodedChars = CharArray(string.length) { ind -> graphicSetState.map(string[ind]) }
    writeDecodedCharacters(decodedChars)
  }

  private fun writeDecodedCharacters(chars: CharArray) {
    model.withContentLock {
      if (cursorYChanged && chars.isNotEmpty()) {
        cursorYChanged = false
        if (model.cursorY > 1) {
          model.setLineWrapped(model.cursorY - 2, false)
        }
      }
      wrapLines()
      val newCursorY = scrollY(model.cursorY)
      model.setCursor(model.cursorX, newCursorY)
      if (chars.isNotEmpty()) {
        val characters = adjustCharBuffer(chars)
        model.writeString(model.cursorX, newCursorY, characters)
        model.setCursor(model.cursorX + characters.size, newCursorY)
      }
    }
  }

  private fun wrapLines() {
    if (model.cursorX >= model.width) {
      // clear the end of the line in the text buffer
      model.eraseLine(model.cursorY - 1, model.width)
      if (isAutoWrap()) {
        model.setLineWrapped(model.cursorY - 1, true)
        model.setCursor(0, model.cursorY + 1)
      }
      else model.setCursor(0, model.cursorY)
    }
  }

  private fun adjustCharBuffer(chars: CharArray): CharArray {
    val dwcCount = CharUtils.countDoubleWidthCharacters(chars, 0, chars.size, settings.ambiguousCharsAreDoubleWidth())
    return if (dwcCount > 0) {
      // Leave gaps for the private use "DWC" character, which simply tells the rendering code to advance one cell.
      val result = CharArray(chars.size + dwcCount)
      var j = 0
      for (i in chars.indices) {
        result[j] = chars[i]
        val codePoint = Character.codePointAt(chars, i)
        val doubleWidthCharacter = CharUtils.isDoubleWidthCharacter(codePoint, settings.ambiguousCharsAreDoubleWidth())
        if (doubleWidthCharacter) {
          j++
          result[j] = CharUtils.DWC
        }
        j++
      }
      result
    }
    else chars
  }

  override fun distanceToLineEnd(): Int = model.width - model.cursorX

  override fun reverseIndex() {
    //Moves the cursor up one line in the same
    //column. If the cursor is at the top margin,
    //the page scrolls down.
    model.withContentLock {
      if (model.cursorY == scrollRegionTop) {
        model.scrollArea(scrollRegionTop, scrollRegionBottom, 1)
      }
      else model.setCursor(model.cursorX, model.cursorY - 1)
    }
  }

  override fun index() {
    //Moves the cursor down one line in the
    //same column. If the cursor is at the
    //bottom margin, the page scrolls up
    model.withContentLock {
      if (model.cursorY == scrollRegionBottom) {
        model.scrollArea(scrollRegionTop, scrollRegionBottom, -1)
      }
      else {
        val newCursorY = model.cursorY + 1
        val newCursorX = adjustX(model.cursorX, newCursorY, -1)
        model.setCursor(newCursorX, newCursorY)
      }
    }
  }

  override fun nextLine() {
    model.withContentLock {
      if (model.cursorY == scrollRegionBottom) {
        model.scrollArea(scrollRegionTop, scrollRegionBottom, -1)
        model.setCursor(0, model.cursorY)
      }
      else model.setCursor(0, model.cursorY + 1)
    }
  }

  override fun fillScreen(c: Char) {
    model.withContentLock {
      val chars = adjustCharBuffer(CharArray(model.width) { c })
      for (row in 1..model.height) {
        model.writeString(0, row, chars)
      }
    }
  }

  override fun saveCursor() {
    storedCursor = StoredCursor(model.cursorX, model.cursorY, model.styleState.current,
                                isAutoWrap(), isOriginMode(), graphicSetState)
  }

  override fun restoreCursor() {
    val cursor = storedCursor
    if (cursor != null) {
      restoreCursor(cursor)
    }
    else { //If nothing was saved by DECSC
      setModeEnabled(TerminalMode.OriginMode, false)  // Resets origin mode (DECOM)
      cursorPosition(1, 1)                              // Moves the cursor to the home position (upper left of screen).
      model.styleState.reset()                                // Turns all character attributes off (normal setting).
      graphicSetState.resetState()
    }
  }

  private fun restoreCursor(storedCursor: StoredCursor) {
    val cursorX = adjustX(storedCursor.cursorX, storedCursor.cursorY, -1)
    model.setCursor(cursorX, storedCursor.cursorY)
    model.styleState.current = storedCursor.textStyle
    setModeEnabled(TerminalMode.AutoWrap, storedCursor.isAutoWrap)
    setModeEnabled(TerminalMode.OriginMode, storedCursor.isOriginMode)
    val designations = storedCursor.designations
    for (i in designations.indices) {
      graphicSetState.designateGraphicSet(i, designations[i])
    }
    graphicSetState.setGL(storedCursor.glMapping)
    graphicSetState.setGR(storedCursor.grMapping)
    if (storedCursor.glOverride >= 0) {
      graphicSetState.overrideGL(storedCursor.glOverride)
    }
  }

  override fun reset() {
    graphicSetState.resetState()

    model.styleState.reset()

    model.clearAll()

    model.isScrollingEnabled = true

    initModes()

    initMouseModes()

    cursorPosition(1, 1)
  }

  private fun initMouseModes() {
    setMouseMode(MouseMode.MOUSE_REPORTING_NONE)
    setMouseFormat(MouseFormat.MOUSE_FORMAT_XTERM)
  }

  override fun characterAttributes(textStyle: TextStyle) {
    model.styleState.current = textStyle
  }

  override fun setScrollingRegion(top: Int, bottom: Int) {
    if (top > bottom) {
      LOG.error("Top margin of scroll region can't be greater then bottom: $top>$bottom")
    }
    scrollRegionTop = max(1, top)
    scrollRegionBottom = min(model.height, bottom)

    //DECSTBM moves the cursor to column 1, line 1 of the page
    cursorPosition(1, 1)
  }

  override fun scrollUp(count: Int) {
    scrollDown(-count)
  }

  override fun scrollDown(count: Int) {
    model.withContentLock {
      model.scrollArea(scrollRegionTop, scrollRegionBottom, count)
    }
  }

  override fun resetScrollRegions() {
    setScrollingRegion(1, model.height)
  }

  override fun cursorHorizontalAbsolute(x: Int) {
    cursorPosition(x, model.cursorY)
  }

  override fun linePositionAbsolute(y: Int) {
    val cursorX = adjustX(model.cursorX, y, -1)
    model.setCursor(cursorX, y)
  }

  override fun cursorPosition(x: Int, y: Int) {
    var cursorY = if (isOriginMode()) y + scrollRegionTop - 1 else y
    cursorY = min(cursorY, scrollingRegionBottom())
    cursorY = max(0, cursorY)

    // avoid issue due to malformed sequence
    var cursorX = max(0, x - 1)
    cursorX = min(cursorX, model.width - 1)
    cursorX = adjustX(cursorX, cursorY, -1)

    model.setCursor(cursorX, cursorY)
  }

  private fun scrollingRegionTop(): Int {
    return if (isOriginMode()) scrollRegionTop else 1
  }

  private fun scrollingRegionBottom(): Int {
    return if (isOriginMode()) scrollRegionBottom else model.height
  }

  override fun cursorUp(countY: Int) {
    model.withContentLock {
      cursorYChanged = true
      val cursorY = max(model.cursorY - countY, scrollingRegionTop())
      val cursorX = adjustX(model.cursorX, cursorY, -1)
      model.setCursor(cursorX, cursorY)
    }
  }

  override fun cursorDown(dY: Int) {
    model.withContentLock {
      cursorYChanged = true
      val cursorY = min(model.cursorY + dY, scrollingRegionBottom())
      val cursorX = adjustX(model.cursorX, cursorY, -1)
      model.setCursor(cursorX, cursorY)
    }
  }

  override fun cursorForward(dX: Int) {
    var cursorX = min(model.cursorX + dX, model.width - 1)
    cursorX = adjustX(cursorX, model.cursorY, 1)
    model.setCursor(cursorX, model.cursorY)
  }

  override fun cursorBackward(dX: Int) {
    var cursorX = max(model.cursorX - dX, 0)
    cursorX = adjustX(cursorX, model.cursorY, -1)
    model.setCursor(cursorX, model.cursorY)
  }

  override fun cursorShape(shape: CursorShape) {
    model.cursorShape = shape
  }

  override fun eraseInLine(arg: Int) {
    model.withContentLock {
      when (arg) {
        0 -> {
          if (model.cursorX < model.width) {
            model.eraseCharacters(model.cursorX, -1, model.cursorY - 1)
          }
          // delete to the end of line : line is no more wrapped
          model.setLineWrapped(model.cursorY - 1, false)
        }
        1 -> {
          val extent = min(model.cursorX + 1, model.width)
          model.eraseCharacters(0, extent, model.cursorY - 1)
        }
        2 -> model.eraseCharacters(0, -1, model.cursorY - 1)
        else -> LOG.warn("Unsupported erase in line mode: $arg")
      }
    }
  }

  override fun deleteCharacters(count: Int) {
    model.withContentLock {
      model.deleteCharacters(model.cursorX, model.cursorY - 1, count)
    }
  }

  override fun getTerminalWidth(): Int = model.width

  override fun getTerminalHeight(): Int = model.height

  override fun eraseInDisplay(arg: Int) {
    model.withContentLock {
      val (beginY, endY) = when (arg) {
        0 -> {
          // Initial line
          if (model.cursorX < model.width) {
            model.eraseCharacters(model.cursorX, -1, model.cursorY - 1)
          }
          // Rest
          model.cursorY to (model.height - 1)
        }
        1 -> {
          // initial line
          model.eraseCharacters(0, model.cursorX + 1, model.cursorY - 1)
          0 to (model.cursorY - 1)
        }
        2 -> {
          model.moveScreenLinesToHistory()
          0 to (model.height - 1)
        }
        else -> {
          LOG.warn("Unsupported erase in display mode: $arg")
          1 to 1
        }
      }
      // Rest of lines
      if (beginY != endY) {
        clearLines(beginY, endY)
      }
    }
  }

  private fun clearLines(beginY: Int, endY: Int) {
    model.withContentLock {
      model.clearLines(beginY, endY)
    }
  }

  override fun setModeEnabled(mode: TerminalMode, enabled: Boolean) {
    if (enabled) {
      terminalModes.add(mode)
    }
    else terminalModes.remove(mode)
    mode.setEnabled(this, enabled)
  }

  private fun initModes() {
    terminalModes.clear()
    setModeEnabled(TerminalMode.AutoWrap, true)
    setModeEnabled(TerminalMode.AutoNewLine, false)
    setModeEnabled(TerminalMode.CursorVisible, true)
    setModeEnabled(TerminalMode.CursorBlinking, true)
  }

  private fun isAutoNewLine(): Boolean = terminalModes.contains(TerminalMode.AutoNewLine)


  private fun isOriginMode(): Boolean = terminalModes.contains(TerminalMode.OriginMode)


  private fun isAutoWrap(): Boolean = terminalModes.contains(TerminalMode.AutoWrap)

  override fun disconnected() {
    model.isCursorVisible = false
  }

  override fun getCursorX(): Int = model.cursorX + 1

  override fun getCursorY(): Int = model.cursorY

  override fun singleShiftSelect(num: Int) {
    graphicSetState.overrideGL(num)
  }

  override fun setWindowTitle(name: String) {
    model.windowTitle = name
  }

  override fun saveWindowTitleOnStack() {
    windowTitlesStack.push(model.windowTitle)
  }

  override fun restoreWindowTitleFromStack() {
    if (!windowTitlesStack.empty()) {
      val title = windowTitlesStack.pop()
      model.windowTitle = title
    }
  }

  override fun clearScreen() {
    clearLines(0, model.height - 1)
  }

  override fun setCursorVisible(visible: Boolean) {
    model.isCursorVisible = visible
  }

  override fun useAlternateBuffer(enabled: Boolean) {
    model.useAlternateBuffer = enabled
    model.isScrollingEnabled = !enabled
  }

  override fun getCodeForKey(key: Int, modifiers: Int): ByteArray? {
    return terminalKeyEncoder.getCode(key, modifiers)
  }

  override fun setApplicationArrowKeys(enabled: Boolean) {
    if (enabled) {
      terminalKeyEncoder.arrowKeysApplicationSequences()
    }
    else terminalKeyEncoder.arrowKeysAnsiCursorSequences()
  }

  override fun setApplicationKeypad(enabled: Boolean) {
    if (enabled) {
      terminalKeyEncoder.keypadApplicationSequences()
    }
    else terminalKeyEncoder.keypadAnsiSequences()
  }

  override fun setAutoNewLine(enabled: Boolean) {
    terminalKeyEncoder.setAutoNewLine(enabled)
  }

  override fun getStyleState(): StyleState = model.styleState

  override fun insertLines(count: Int) {
    model.withContentLock {
      model.insertLines(model.cursorY - 1, count, scrollRegionBottom)
    }
  }

  override fun deleteLines(count: Int) {
    model.withContentLock {
      model.deleteLines(model.cursorY - 1, count, scrollRegionBottom)
    }
  }

  override fun setBlinkingCursor(enabled: Boolean) {
    model.isCursorBlinking = enabled
  }

  override fun eraseCharacters(count: Int) {
    //Clear the next n characters on the cursor's line, including the cursor's
    //position.
    model.withContentLock {
      model.eraseCharacters(model.cursorX, model.cursorX + count, model.cursorY - 1)
    }
  }

  override fun insertBlankCharacters(count: Int) {
    model.withContentLock {
      val extent = min(count, model.width - model.cursorX)
      model.insertBlankCharacters(model.cursorX, model.cursorY - 1, extent)
    }
  }

  override fun clearTabStopAtCursor() {
    tabulator.clearTabStop(model.cursorX)
  }

  override fun clearAllTabStops() {
    tabulator.clearAllTabStops()
  }

  override fun setTabStopAtCursor() {
    tabulator.setTabStop(model.cursorX)
  }

  override fun writeUnwrappedString(string: String) {
    val length = string.length
    var ind = 0
    while (ind < length) {
      val amountInLine = min(distanceToLineEnd(), length - ind)
      writeCharacters(string.substring(ind, ind + amountInLine))
      wrapLines()
      val cursorY = scrollY(model.cursorY)
      model.setCursor(model.cursorX, cursorY)
      ind += amountInLine
    }
  }

  override fun setMouseMode(mode: MouseMode) {
    model.mouseMode = mode
  }

  override fun setMouseFormat(mouseFormat: MouseFormat) {
    model.mouseFormat = mouseFormat
  }

  override fun setAltSendsEscape(enabled: Boolean) {
    terminalKeyEncoder.setAltSendsEscape(enabled)
  }

  override fun setTerminalOutput(terminalOutput: TerminalOutputStream?) {
    // TODO: Is it needed?
  }

  override fun deviceStatusReport(str: String?) {
    // TODO: Is it needed?
  }

  override fun deviceAttributes(response: ByteArray?) {
    // TODO: Is it needed?
  }

  override fun setLinkUriStarted(uri: String) {
    val style: TextStyle = model.styleState.current
    model.styleState.current = HyperlinkStyle(style, LinkInfo {
      try {
        Desktop.getDesktop().browse(URI(uri))
      }
      catch (ignored: Exception) {
      }
    })
  }

  override fun setLinkUriFinished() {
    val current: TextStyle = model.styleState.current
    if (current is HyperlinkStyle) {
      current.prevTextStyle?.let { model.styleState.current = it }
    }
  }

  override fun setBracketedPasteMode(enabled: Boolean) {
    model.isBracketedPasteMode = enabled
  }

  override fun getWindowForeground(): com.jediterm.core.Color {
    return settings.terminalColorPalette.getForeground(model.styleState.foreground)
  }

  override fun getWindowBackground(): com.jediterm.core.Color {
    return settings.terminalColorPalette.getBackground(model.styleState.background)
  }

  private val customCommandListeners: MutableList<TerminalCustomCommandListener> = CopyOnWriteArrayList()

  override fun addCustomCommandListener(listener: TerminalCustomCommandListener) {
    customCommandListeners.add(listener)
  }

  override fun removeCustomCommandListener(listener: TerminalCustomCommandListener) {
    customCommandListeners.remove(listener)
  }

  override fun processCustomCommand(args: MutableList<String>) {
    for (customCommandListener in customCommandListeners) {
      customCommandListener.process(args)
    }
  }

  private class DefaultTabulator(private var myWidth: Int,
                                 private val myTabLength: Int = TAB_LENGTH) : Tabulator {
    private val myTabStops: SortedSet<Int> = TreeSet()

    init {
      initTabStops(myWidth, myTabLength)
    }

    private fun initTabStops(columns: Int, tabLength: Int) {
      var i = tabLength
      while (i < columns) {
        myTabStops.add(i)
        i += tabLength
      }
    }

    override fun resize(columns: Int) {
      if (columns > myWidth) {
        var i = myTabLength * (myWidth / myTabLength)
        while (i < columns) {
          if (i >= myWidth) {
            myTabStops.add(i)
          }
          i += myTabLength
        }
      }
      else {
        val it = myTabStops.iterator()
        while (it.hasNext()) {
          val i = it.next()
          if (i > columns) {
            it.remove()
          }
        }
      }
      myWidth = columns
    }

    override fun clearTabStop(position: Int) {
      myTabStops.remove(position)
    }

    override fun clearAllTabStops() {
      myTabStops.clear()
    }

    override fun getNextTabWidth(position: Int): Int {
      return nextTab(position) - position
    }

    override fun getPreviousTabWidth(position: Int): Int {
      return position - previousTab(position)
    }

    override fun nextTab(position: Int): Int {
      // Search for the first tab stop after the given position...
      val tailSet = myTabStops.tailSet(position + 1)
      val tabStop = if (!tailSet.isEmpty()) tailSet.first() else Int.MAX_VALUE

      // Don't go beyond the end of the line...
      return min(tabStop, myWidth - 1)
    }

    override fun previousTab(position: Int): Int {
      // Search for the first tab stop before the given position...
      val headSet = myTabStops.headSet(position)
      val tabStop = if (!headSet.isEmpty()) headSet.last() else 0

      // Don't go beyond the start of the line...
      return max(0, tabStop)
    }

    override fun setTabStop(position: Int) {
      myTabStops.add(position)
    }

    companion object {
      private const val TAB_LENGTH = 8
    }
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(TerminalController::class.java)
  }
}