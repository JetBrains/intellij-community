package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateCapture
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerState.Companion.notMatched
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.TextMateByteOffset
import org.jetbrains.plugins.textmate.regex.TextMateCharOffset
import org.jetbrains.plugins.textmate.regex.TextMateCharRange
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.jetbrains.plugins.textmate.regex.byteOffset
import org.jetbrains.plugins.textmate.regex.byteOffsetByCharOffset
import org.jetbrains.plugins.textmate.regex.charOffset
import org.jetbrains.plugins.textmate.regex.get
import org.jetbrains.plugins.textmate.regex.indexOf
import org.jetbrains.plugins.textmate.regex.subSequence
import kotlin.math.min

class TextMateLexerCore(
  private val languageDescriptor: TextMateLanguageDescriptor,
  private val mySyntaxMatcher: TextMateSyntaxMatcher,
  private val myLineLimit: Int,
  private val myStripWhitespaces: Boolean,
) {

  private var myCurrentOffset: TextMateCharOffset = 0.charOffset()
  private var myText: CharSequence = ""
  private var myCurrentScope: TextMateScope = TextMateScope.EMPTY
  private var myNestedScope = ArrayDeque<Int>()
  private var myStates = persistentListOf<TextMateLexerState>()

  fun getCurrentOffset(): Int {
    return myCurrentOffset.offset
  }

  fun getCurrentCharOffset(): TextMateCharOffset {
    return myCurrentOffset
  }

  fun init(text: CharSequence, startCharOffset: Int) {
    init(text, startCharOffset.charOffset())
  }

  fun init(text: CharSequence, startCharOffset: TextMateCharOffset) {
    myText = text
    myCurrentOffset = startCharOffset

    myStates = persistentListOf(notMatched(languageDescriptor.rootSyntaxNode))
    myCurrentScope = TextMateScope(languageDescriptor.rootScopeName, null)
    myNestedScope = ArrayDeque<Int>().also { it.addLast(1) }
  }

  fun advanceLine(checkCancelledCallback: Runnable?): MutableList<TextmateToken> {
    val startLineOffset = myCurrentOffset
    val endLineOffset = myText.indexOf('\n', startIndex = startLineOffset).let {
      if (it.offset == -1) myText.length.charOffset() else TextMateCharOffset(it.offset + 1)
    }

    val lineCharSequence = myText.subSequence(startLineOffset, endLineOffset)
    return if (myLineLimit >= 0 && lineCharSequence.length > myLineLimit) {
      val output = mutableListOf<TextmateToken>()
      myStates = parseLine(line = lineCharSequence.subSequence(0, myLineLimit),
                           output = output,
                           states = myStates,
                           lineStartOffset = startLineOffset,
                           linePosition = 0.charOffset(),
                           lineByteOffset = 0.byteOffset(),
                           injections = languageDescriptor.injections,
                           checkWhileConditions = true,
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
                           linePosition = 0.charOffset(),
                           lineByteOffset = 0.byteOffset(),
                           injections = languageDescriptor.injections,
                           checkWhileConditions = true,
                           checkCancelledCallback = checkCancelledCallback)
      output
    }
  }

  private fun parseLine(
    line: CharSequence,
    output: MutableList<TextmateToken>,
    states: PersistentList<TextMateLexerState>,
    lineStartOffset: TextMateCharOffset,
    linePosition: TextMateCharOffset,
    lineByteOffset: TextMateByteOffset,
    injections: List<InjectionNodeDescriptor>,
    checkWhileConditions: Boolean,
    checkCancelledCallback: Runnable?,
  ): PersistentList<TextMateLexerState> {
    var states = states
    var linePosition = linePosition
    var lineByteOffset = lineByteOffset
    var lastSuccessState = states
    var lastSuccessStateOccursCount = 0
    var lastMovedOffset = lineStartOffset

    val matchBeginString = lineStartOffset.offset == 0 && linePosition.offset == 0
    // makes sense only for a line, cannot be used across lines;
    // when the topmost begin match consumed the trailing newline of the previous line,
    // \G matches at the beginning of this line.
    var anchorByteOffset = if (checkWhileConditions && states.last().matchedEOL) 0.byteOffset() else (-1).byteOffset()

    return mySyntaxMatcher.matchingString(line) { string ->
      if (checkWhileConditions) {
        // Check the while-conditions of the rules currently on the stack from the outermost to the innermost rule.
        // When a while-condition fails, its rule together with every rule
        // nested inside it is discarded from the stack, and the conditions of the nested rules are not checked.
        for ((index, state) in states.withIndex()) {
          if (state.syntaxRule.getStringAttribute(Constants.StringKey.WHILE) != null) {
            val matchWhile = mySyntaxMatcher.matchStringRegex(keyName = Constants.StringKey.WHILE,
                                                              string = string,
                                                              byteOffset = lineByteOffset,
                                                              matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                              matchBeginString = matchBeginString,
                                                              lexerState = state,
                                                              checkCancelledCallback = checkCancelledCallback)
            if (matchWhile.matched) {
              // todo: support whileCaptures
              anchorByteOffset = matchWhile.byteRange().end
            }
            else {
              // The while-condition failed: discard this rule together with every rule nested inside it.
              // Each discarded rule opened a name and a content-name selector, so close two selectors per rule,
              // the innermost rule first.
              repeat(states.size - index) {
                closeScopeSelector(output, linePosition + lineStartOffset) // closing content scope
                closeScopeSelector(output, linePosition + lineStartOffset) // closing basic scope
              }
              states = states.subList(0, index).toPersistentList()
              break
            }
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
                                                     injections = injections,
                                                     checkCancelledCallback = checkCancelledCallback)
      val currentRule = currentState.syntaxRule
      val currentMatch = currentState.matchData

        var endPosition: TextMateCharOffset
        val endMatch = mySyntaxMatcher.matchStringRegex(keyName = Constants.StringKey.END,
                                                        string = string,
                                                        byteOffset = lineByteOffset,
                                                        matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                        matchBeginString = matchBeginString,
                                                        lexerState = lastState,
                                                        checkCancelledCallback = checkCancelledCallback)
        val lineLength = line.length.charOffset()
        // by default the `end` pattern wins over the nested patterns when both match at the same offset.
        // `applyEndPatternLast` inverts this tie-break so that the nested patterns are applied first
        // and the `end` pattern is applied only when it matches strictly before the nested match.
        val applyEndPatternLast = isApplyEndPatternLast(lastRule)
        if (endMatch.matched && (!currentMatch.matched || endWinsOverCurrent(applyEndPatternLast, currentMatch, endMatch) || lastState == currentState)) {
          val poppedState = states.last()
          if (poppedState.matchData.matched && !poppedState.matchedEOL) {
            // if begin hasn't matched EOL, it was performed on the same line; we need to use its anchor
            anchorByteOffset = poppedState.matchData.byteRange().end
          }
          states = states.removingAt(states.size - 1)

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
            addToken(output, lineLength + lineStartOffset)
            break
          }
          localStates.remove(poppedState)
        }
        else if (currentMatch.matched) {
          anchorByteOffset = currentMatch.byteRange().end

          val currentRange = currentMatch.charRange(string)
          val startPosition = currentRange.start
          endPosition = currentRange.end

          if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
            states = states.adding(currentState)

            val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
            openScopeSelector(output, name, startPosition + lineStartOffset)

            parseCaptures(output, Constants.CaptureKey.BEGIN_CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states,
                          checkCancelledCallback) ||
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
            addToken(output, lineLength + lineStartOffset)
            break
          }
          localStates.add(currentState)
        }
        else {
          addToken(output, lineLength + lineStartOffset)
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
            addToken(output, lineLength + lineStartOffset)
            break
          }
          lastSuccessStateOccursCount++
        }

        if (linePosition != endPosition) {
          lineByteOffset += byteOffsetByCharOffset(line, linePosition, endPosition)
          linePosition = endPosition
        }

        checkCancelledCallback?.run()
      }
      states
    }
  }

  private fun parseCaptures(
    output: MutableList<TextmateToken>,
    captureKey: Constants.CaptureKey,
    rule: SyntaxNodeDescriptor,
    matchData: MatchData,
    string: TextMateString,
    line: CharSequence,
    startLineOffset: TextMateCharOffset,
    states: PersistentList<TextMateLexerState>,
    checkCancelledCallback: Runnable?,
  ): Boolean {
    val captures = rule.getCaptureRules(captureKey) ?: return false

    val matchByteEnd = matchData.byteRange().end
    val activeCaptureRanges = ArrayDeque<TextMateCharRange>()
    for (group in 0..<matchData.count()) {
      val capture = if (group < captures.size) captures[group] else null
      if (capture == null) {
        continue
      }

      val byteRange = matchData.byteRange(group)
      if (byteRange.isEmpty) {
        continue
      }
      if (byteRange.start > matchByteEnd) {
        // the group is captured beyond the consumed match, e.g. inside a lookahead
        break
      }

      val captureRange = matchData.charRange(string, group)

      while (!activeCaptureRanges.isEmpty() && activeCaptureRanges.last().end <= captureRange.start) {
        closeScopeSelector(output, startLineOffset + activeCaptureRanges.removeLast().end)
      }

      val captureName = when (capture) {
        is TextMateCapture.Name -> capture.name
        is TextMateCapture.Rule -> capture.node.getStringAttribute(Constants.StringKey.NAME)
      }

      if (captureName != null) {
        val scopeName = if (rule.hasBackReference(captureKey, group)) {
          replaceGroupsWithMatchDataInCaptures(captureName, string, matchData)
        }
        else {
          captureName
        }
        var selectorStartOffset = 0.charOffset()
        var indexOfSpace = scopeName.indexOf(char = ' ', startIndex = selectorStartOffset)
        if (indexOfSpace.offset == -1) {
          openScopeSelector(output, scopeName, startLineOffset + captureRange.start)
          activeCaptureRanges.addLast(captureRange)
        }
        else {
          while (indexOfSpace.offset >= 0) {
            openScopeSelector(output, scopeName.subSequence(selectorStartOffset, indexOfSpace), startLineOffset + captureRange.start)
            selectorStartOffset = TextMateCharOffset(indexOfSpace.offset + 1)
            indexOfSpace = scopeName.indexOf(char = ' ', startIndex = selectorStartOffset)
            activeCaptureRanges.addLast(captureRange)
          }
          openScopeSelector(output, scopeName.subSequence(selectorStartOffset, scopeName.length.charOffset()), startLineOffset + captureRange.start)
          activeCaptureRanges.addLast(captureRange)
        }
      }
      if (capture is TextMateCapture.Rule) {
        val capturedString = line.subSequence(0.charOffset(), captureRange.end)
        mySyntaxMatcher.matchingString(capturedString) { capturedTextMateString ->
          val captureState = TextMateLexerState(syntaxRule = capture.node,
                                                matchData = matchData,
                                                priorityMatch = TextMateWeigh.Priority.NORMAL,
                                                enterByteOffset = byteRange.start,
                                                line = capturedTextMateString)
          parseLine(line = capturedString,
                    output = output,
                    states = states.adding(captureState),
                    lineStartOffset = startLineOffset,
                    linePosition = captureRange.start,
                    lineByteOffset = byteRange.start,
                    injections = emptyList(),
                    checkWhileConditions = false,
                    checkCancelledCallback = checkCancelledCallback)
        }
      }
    }
    while (!activeCaptureRanges.isEmpty()) {
      closeScopeSelector(output, startLineOffset + activeCaptureRanges.removeLast().end)
    }
    return true
  }

  private fun openScopeSelector(output: MutableList<TextmateToken>, name: CharSequence?, position: TextMateCharOffset) {
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

  private fun closeScopeSelector(output: MutableList<TextmateToken>, position: TextMateCharOffset) {
    val lastOpenedName = myCurrentScope.scopeName
    if (!lastOpenedName.isNullOrEmpty()) {
      addToken(output, position)
    }
    myNestedScope.removeLastOrNull()?.let { nestingLevel ->
      repeat(nestingLevel) {
        myCurrentScope = myCurrentScope.parent ?: myCurrentScope
      }
    }
  }

  private fun addToken(output: MutableList<TextmateToken>, position: TextMateCharOffset) {
    val position = min(position.offset, myText.length).charOffset()
    if (position > myCurrentOffset) {
      var restartable = myCurrentScope.parent == null
      val wsStart = myCurrentOffset
      while (myStripWhitespaces && position > myCurrentOffset && myText[myCurrentOffset].isWhitespace()) {
        myCurrentOffset = TextMateCharOffset(myCurrentOffset.offset + 1)
      }

      if (wsStart < myCurrentOffset) {
        output.add(TextmateToken(scope = TextMateScope.WHITESPACE,
                                 startCharOffset = wsStart,
                                 endCharOffset = myCurrentOffset,
                                 restartable = restartable))
        restartable = false
      }

      var wsEnd = position
      while (myStripWhitespaces && wsEnd > myCurrentOffset && myText[wsEnd.offset - 1].isWhitespace()) {
        wsEnd = TextMateCharOffset(wsEnd.offset - 1)
      }

      if (myCurrentOffset < wsEnd) {
        output.add(TextmateToken(scope = myCurrentScope,
                                 startCharOffset = myCurrentOffset,
                                 endCharOffset = wsEnd,
                                 restartable = restartable))
      }

      if (wsEnd < position) {
        output.add(TextmateToken(scope = TextMateScope.WHITESPACE,
                                 startCharOffset = wsEnd,
                                 endCharOffset = position,
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

    private fun isApplyEndPatternLast(syntaxRule: SyntaxNodeDescriptor): Boolean {
      val value = syntaxRule.getStringAttribute(Constants.StringKey.APPLY_END_PATTERN_LAST)
      return value != null && (value.contentEquals("1") || value.contentEquals("true", ignoreCase = true))
    }

    /**
     * Decides whether the `end` match should be applied instead of the nested (current) match.
     * Both matches are expected to be matched. With [applyEndPatternLast] the `end` pattern wins only
     * when it matches strictly before the nested pattern; otherwise it also wins the ties.
     */
    private fun endWinsOverCurrent(applyEndPatternLast: Boolean, currentMatch: MatchData, endMatch: MatchData): Boolean {
      return if (applyEndPatternLast) {
        currentMatch.byteRange().start > endMatch.byteRange().start
      }
      else {
        currentMatch.byteRange().start >= endMatch.byteRange().start
      }
    }

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
