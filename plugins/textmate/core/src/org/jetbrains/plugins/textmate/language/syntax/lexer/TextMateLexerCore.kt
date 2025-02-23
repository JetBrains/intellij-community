package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateCapture
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerState.Companion.notMatched
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateRange
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.jetbrains.plugins.textmate.regex.byteOffsetByCharOffset
import kotlin.math.min

class TextMateLexerCore(
  private val languageDescriptor: TextMateLanguageDescriptor,
  private val mySyntaxMatcher: TextMateSyntaxMatcher,
  private val myLineLimit: Int,
  private val myStripWhitespaces: Boolean,
) {

  private var myCurrentOffset: Int = 0
  private var myText: CharSequence = ""
  private var myCurrentScope: TextMateScope = TextMateScope.EMPTY
  private var myNestedScope = ArrayDeque<Int>()
  private var myStates = persistentListOf<TextMateLexerState>()

  fun getCurrentOffset(): Int {
    return myCurrentOffset
  }

  fun init(text: CharSequence, startOffset: Int) {
    myText = text
    myCurrentOffset = startOffset

    myStates = persistentListOf(notMatched(languageDescriptor.rootSyntaxNode))
    myCurrentScope = TextMateScope(languageDescriptor.scopeName, null)
    myNestedScope = ArrayDeque<Int>().also { it.addLast(1) }
  }

  fun advanceLine(checkCancelledCallback: Runnable?): MutableList<TextmateToken> {
    val startLineOffset = myCurrentOffset
    val endLineOffset = myText.indexOf('\n', startIndex = startLineOffset).let {
      if (it == -1) myText.length else it + 1
    }

    val lineCharSequence = myText.subSequence(startLineOffset, endLineOffset)
    return if (myLineLimit >= 0 && lineCharSequence.length > myLineLimit) {
      val output = mutableListOf<TextmateToken>()
      myStates = parseLine(line = lineCharSequence.subSequence(0, myLineLimit),
                           output = output,
                           states = myStates,
                           lineStartOffset = startLineOffset,
                           linePosition = 0,
                           lineByteOffset = 0,
                           checkCancelledCallback = checkCancelledCallback)
      addToken(output, endLineOffset)
      output
    }
    else {
      val output = mutableListOf<TextmateToken>()
      myStates = parseLine(line = lineCharSequence,
                           output = output,
                           states = myStates,
                           lineStartOffset = startLineOffset,
                           linePosition = 0,
                           lineByteOffset = 0,
                           checkCancelledCallback = checkCancelledCallback)
      output
    }
  }

  private fun parseLine(
    line: CharSequence,
    output: MutableList<TextmateToken>,
    states: PersistentList<TextMateLexerState>,
    lineStartOffset: Int,
    linePosition: Int,
    lineByteOffset: Int,
    checkCancelledCallback: Runnable?,
  ): PersistentList<TextMateLexerState> {
    var states = states
    var linePosition = linePosition
    var lineByteOffset = lineByteOffset
    var lastSuccessState = states
    var lastSuccessStateOccursCount = 0
    var lastMovedOffset = lineStartOffset

    val matchBeginString = lineStartOffset == 0 && linePosition == 0
    var anchorByteOffset = -1 // makes sense only for a line, cannot be used across lines

    val string = mySyntaxMatcher.createStringToMatch(line)

    var whileStates = states
    while (!whileStates.isEmpty()) {
      val whileState = whileStates.last()
      whileStates = whileStates.removeAt(whileStates.size - 1)
      if (whileState.syntaxRule.getStringAttribute(Constants.StringKey.WHILE) != null) {
        val matchWhile = mySyntaxMatcher.matchStringRegex(keyName = Constants.StringKey.WHILE,
                                                          string = string,
                                                          byteOffset = lineByteOffset,
                                                          matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                          matchBeginString = matchBeginString,
                                                          lexerState = whileState,
                                                          checkCancelledCallback = checkCancelledCallback)
        if (matchWhile.matched) {
          // todo: support whileCaptures
          if (anchorByteOffset == -1) {
            anchorByteOffset = matchWhile.byteOffset().end
          }
        }
        else {
          closeScopeSelector(output, linePosition + lineStartOffset)
          closeScopeSelector(output, linePosition + lineStartOffset)
          states = whileStates
          anchorByteOffset = -1
        }
      }
    }

    val localStates = mutableSetOf<TextMateLexerState>()
    while (true) {
      val lastState = states.last()
      val lastRule = lastState.syntaxRule

      val currentState = mySyntaxMatcher.matchRule(syntaxNodeDescriptor = lastRule,
                                                   string = string,
                                                   byteOffset = lineByteOffset,
                                                   matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                   matchBeginString = matchBeginString,
                                                   priority = TextMateWeigh.Priority.NORMAL,
                                                   currentScope = myCurrentScope,
                                                   checkCancelledCallback = checkCancelledCallback)
      val currentRule = currentState.syntaxRule
      val currentMatch = currentState.matchData

      var endPosition: Int
      val endMatch = mySyntaxMatcher.matchStringRegex(keyName = Constants.StringKey.END,
                                                      string = string,
                                                      byteOffset = lineByteOffset,
                                                      matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                      matchBeginString = matchBeginString,
                                                      lexerState = lastState,
                                                      checkCancelledCallback = checkCancelledCallback)
      if (endMatch.matched && (!currentMatch.matched || currentMatch.byteOffset().start >= endMatch.byteOffset().start || lastState == currentState)) {
        // todo: applyEndPatternLast
        val poppedState = states.last()
        if (poppedState.matchData.matched && !poppedState.matchedEOL) {
          // if begin hasn't matched EOL, it was performed on the same line; we need to use its anchor
          anchorByteOffset = poppedState.matchData.byteOffset().end
        }
        states = states.removeAt(states.size - 1)

        val endRange = endMatch.charRange(string)
        endPosition = endRange.start
        val startPosition = endPosition
        closeScopeSelector(output, startPosition + lineStartOffset) // closing content scope
        if (lastRule.getCaptureRules(Constants.CaptureKey.END_CAPTURES) == null && lastRule.getCaptureRules(Constants.CaptureKey.CAPTURES) == null ||
            parseCaptures(output, Constants.CaptureKey.END_CAPTURES, lastRule, endMatch, string, line, lineStartOffset, states, checkCancelledCallback) ||
            parseCaptures(output, Constants.CaptureKey.CAPTURES, lastRule, endMatch, string, line, lineStartOffset, states, checkCancelledCallback)) {
          // move line position only if anything was captured or if there is nothing to capture at all
          endPosition = endRange.end
        }
        closeScopeSelector(output, endPosition + lineStartOffset) // closing basic scope

        if (linePosition == endPosition && containsLexerState(localStates, poppedState) && poppedState.enterByteOffset == lineByteOffset) {
          addToken(output, line.length + lineStartOffset)
          break
        }
        localStates.remove(poppedState)
      }
      else if (currentMatch.matched) {
        anchorByteOffset = currentMatch.byteOffset().end

        val currentRange = currentMatch.charRange(string)
        val startPosition = currentRange.start
        endPosition = currentRange.end

        if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
          states = states.add(currentState)

          val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
          openScopeSelector(output, name, startPosition + lineStartOffset)

          parseCaptures(output, Constants.CaptureKey.BEGIN_CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states,
                        checkCancelledCallback)
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states,
                        checkCancelledCallback)

          val contentName = getStringAttribute(Constants.StringKey.CONTENT_NAME, currentRule, string, currentMatch)
          openScopeSelector(output, contentName, endPosition + lineStartOffset)
        }
        else if (currentRule.getStringAttribute(Constants.StringKey.MATCH) != null) {
          val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
          openScopeSelector(output, name, startPosition + lineStartOffset)
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states,
                        checkCancelledCallback)
          closeScopeSelector(output, endPosition + lineStartOffset)
        }

        if (linePosition == endPosition && containsLexerState(localStates, currentState)) {
          addToken(output, line.length + lineStartOffset)
          break
        }
        localStates.add(currentState)
      }
      else {
        addToken(output, line.length + lineStartOffset)
        break
      }

      // global looping protection
      if (lastMovedOffset < myCurrentOffset) {
        lastSuccessState = states
        lastSuccessStateOccursCount = 0
        lastMovedOffset = myCurrentOffset
      }
      else if (lastSuccessState == states) {
        if (lastSuccessStateOccursCount > MAX_LOOPS_COUNT) {
          addToken(output, line.length + lineStartOffset)
          break
        }
        lastSuccessStateOccursCount++
      }

      if (linePosition != endPosition) {
        lineByteOffset = lineByteOffset + byteOffsetByCharOffset(line, linePosition, endPosition)
        linePosition = endPosition
      }

      checkCancelledCallback?.run()
    }
    return states
  }

  private fun parseCaptures(
    output: MutableList<TextmateToken>,
    captureKey: Constants.CaptureKey,
    rule: SyntaxNodeDescriptor,
    matchData: MatchData,
    string: TextMateString,
    line: CharSequence,
    startLineOffset: Int,
    states: PersistentList<TextMateLexerState>,
    checkCancelledCallback: Runnable?,
  ): Boolean {
    val captures = rule.getCaptureRules(captureKey)
    if (captures == null) {
      return false
    }

    val activeCaptureRanges = ArrayDeque<TextMateRange>()
    for (group in 0..<matchData.count()) {
      val capture = if (group < captures.size) captures[group] else null
      if (capture == null) {
        continue
      }

      val byteRange = matchData.byteOffset(group)
      if (byteRange.isEmpty) {
        continue
      }

      val captureRange = matchData.charRange(string, group)

      while (!activeCaptureRanges.isEmpty() && activeCaptureRanges.last().end <= captureRange.start) {
        closeScopeSelector(output, startLineOffset + activeCaptureRanges.removeLast().end)
      }

      if (capture is TextMateCapture.Name) {
        val captureName = capture.name
        val scopeName = if (rule.hasBackReference(captureKey, group)) {
          replaceGroupsWithMatchDataInCaptures(captureName, string, matchData)
        }
        else {
          captureName
        }
        var selectorStartOffset = 0
        var indexOfSpace = scopeName.indexOf(char = ' ', startIndex = selectorStartOffset, ignoreCase = false)
        if (indexOfSpace == -1) {
          openScopeSelector(output, scopeName, startLineOffset + captureRange.start)
          activeCaptureRanges.addLast(captureRange)
        }
        else {
          while (indexOfSpace >= 0) {
            openScopeSelector(output, scopeName.subSequence(selectorStartOffset, indexOfSpace), startLineOffset + captureRange.start)
            selectorStartOffset = indexOfSpace + 1
            indexOfSpace = scopeName.indexOf(char = ' ', startIndex = selectorStartOffset, ignoreCase = false)
            activeCaptureRanges.addLast(captureRange)
          }
          openScopeSelector(output, scopeName.subSequence(selectorStartOffset, scopeName.length), startLineOffset + captureRange.start)
          activeCaptureRanges.addLast(captureRange)
        }
      }
      else if (capture is TextMateCapture.Rule) {
        val capturedString = line.subSequence(0, captureRange.end)
        val capturedTextMateString = mySyntaxMatcher.createStringToMatch(capturedString)
        val captureState = TextMateLexerState(syntaxRule = capture.node,
                                              matchData = matchData,
                                              priorityMatch = TextMateWeigh.Priority.NORMAL,
                                              enterByteOffset = byteRange.start,
                                              line = capturedTextMateString)
        parseLine(line = capturedString,
                  output = output,
                  states = states.add(captureState),
                  lineStartOffset = startLineOffset,
                  linePosition = captureRange.start,
                  lineByteOffset = byteRange.start,
                  checkCancelledCallback = checkCancelledCallback)
      }
      else {
        error("unknown capture type: $capture")
      }
    }
    while (!activeCaptureRanges.isEmpty()) {
      closeScopeSelector(output, startLineOffset + activeCaptureRanges.removeLast().end)
    }
    return true
  }

  private fun openScopeSelector(output: MutableList<TextmateToken>, name: CharSequence?, position: Int) {
    addToken(output, position)
    var count = 0
    var prevIndexOfSpace = 0
    if (name != null) {
      var indexOfSpace = name.indexOf(char = ' ', startIndex = 0, ignoreCase = false)
      while (indexOfSpace >= 0) {
        myCurrentScope = myCurrentScope.add(name.subSequence(prevIndexOfSpace, indexOfSpace))
        prevIndexOfSpace = indexOfSpace + 1
        indexOfSpace = name.indexOf(char = ' ', startIndex = prevIndexOfSpace, ignoreCase = false)
        count++
      }
    }
    myCurrentScope = myCurrentScope.add(name?.subSequence(prevIndexOfSpace, name.length))
    myNestedScope.addLast(count + 1)
  }

  private fun closeScopeSelector(output: MutableList<TextmateToken>, position: Int) {
    val lastOpenedName = myCurrentScope.scopeName
    if (lastOpenedName != null && !lastOpenedName.isEmpty()) {
      addToken(output, position)
    }
    myNestedScope.removeLastOrNull()?.let { nestingLevel ->
      repeat(nestingLevel) {
        myCurrentScope = myCurrentScope.parent ?: myCurrentScope
      }
    }
  }

  private fun addToken(output: MutableList<TextmateToken>, position: Int) {
    val position = min(position, myText.length)
    if (position > myCurrentOffset) {
      var restartable = myCurrentScope.parent == null
      val wsStart = myCurrentOffset
      while (myStripWhitespaces && position > myCurrentOffset && myText[myCurrentOffset].isWhitespace()) {
        myCurrentOffset++
      }

      if (wsStart < myCurrentOffset) {
        output.add(TextmateToken(scope = TextMateScope.WHITESPACE,
                                 startOffset = wsStart,
                                 endOffset = myCurrentOffset,
                                 restartable = restartable))
        restartable = false
      }

      var wsEnd = position
      while (myStripWhitespaces && wsEnd > myCurrentOffset && myText[wsEnd - 1].isWhitespace()) {
        wsEnd--
      }

      if (myCurrentOffset < wsEnd) {
        output.add(TextmateToken(scope = myCurrentScope,
                                 startOffset = myCurrentOffset,
                                 endOffset = wsEnd,
                                 restartable = restartable))
      }

      if (wsEnd < position) {
        output.add(TextmateToken(scope = TextMateScope.WHITESPACE,
                                 startOffset = wsEnd,
                                 endOffset = position,
                                 restartable = restartable))
      }

      myCurrentOffset = position
    }
  }

  companion object {
    /**
     * Count of `lastSuccessState` that can be occurred again without offset changing.
     * If `lastSuccessStateOccursCount` reaches `MAX_LOOPS_COUNT`
     * then lexing of current line stops and lexer moved to the EOL.
     */
    private const val MAX_LOOPS_COUNT = 10

    private fun containsLexerState(states: MutableSet<TextMateLexerState>, state: TextMateLexerState): Boolean {
      for (s in states) {
        if (s.enterByteOffset == state.enterByteOffset && s.syntaxRule == state.syntaxRule) {
          return true
        }
      }
      return false
    }

    private fun getStringAttribute(
      keyName: Constants.StringKey,
      syntaxRule: SyntaxNodeDescriptor,
      string: TextMateString,
      matchData: MatchData
    ): CharSequence? {
      val stringAttribute = syntaxRule.getStringAttribute(keyName)
      return when {
        stringAttribute == null -> null
        syntaxRule.hasBackReference(keyName) -> replaceGroupsWithMatchDataInCaptures(stringAttribute, string, matchData)
        else -> stringAttribute
      }
    }
  }
}
