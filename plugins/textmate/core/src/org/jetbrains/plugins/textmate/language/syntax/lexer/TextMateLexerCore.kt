package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
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
  private var myStackFrames = persistentListOf<TextMateStackFrame>()

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
    myStackFrames = persistentListOf(TextMateStackFrame(state = notMatched(languageDescriptor.rootSyntaxNode),
                                                        scopes = TextMateScopeStack(TextMateScope(languageDescriptor.rootScopeName, null))))
  }

  fun advanceLine(checkCancelledCallback: Runnable?): List<TextmateToken> {
    val startLineOffset = myCurrentOffset
    val endLineOffset = myText.indexOf('\n', startIndex = startLineOffset).let {
      if (it.offset == -1) myText.length.charOffset() else TextMateCharOffset(it.offset + 1)
    }

    val lineCharSequence = myText.subSequence(startLineOffset, endLineOffset)
    return buildList {
      val output = this
      if (myLineLimit >= 0 && lineCharSequence.length > myLineLimit) {
        myStackFrames = parseLine(line = lineCharSequence.subSequence(0, myLineLimit),
                                  output = output,
                                  stackFrames = myStackFrames,
                                  lineStartOffset = startLineOffset,
                                  linePosition = 0.charOffset(),
                                  lineByteOffset = 0.byteOffset(),
                                  injections = languageDescriptor.injections,
                                  checkWhileConditions = true,
                                  checkCancelledCallback = checkCancelledCallback)
        addToken(output, myStackFrames.last().scopes.currentScope, endLineOffset)
      }
      else {
        myStackFrames = parseLine(line = lineCharSequence,
                                  output = output,
                                  stackFrames = myStackFrames,
                                  lineStartOffset = startLineOffset,
                                  linePosition = 0.charOffset(),
                                  lineByteOffset = 0.byteOffset(),
                                  injections = languageDescriptor.injections,
                                  checkWhileConditions = true,
                                  checkCancelledCallback = checkCancelledCallback)
      }
    }
  }

  private fun parseLine(
    line: CharSequence,
    output: MutableList<TextmateToken>,
    stackFrames: PersistentList<TextMateStackFrame>,
    lineStartOffset: TextMateCharOffset,
    linePosition: TextMateCharOffset,
    lineByteOffset: TextMateByteOffset,
    injections: List<InjectionNodeDescriptor>,
    checkWhileConditions: Boolean,
    checkCancelledCallback: Runnable?,
  ): PersistentList<TextMateStackFrame> {
    var stackFrames = stackFrames
    var lastSuccessStackFrames = stackFrames
    var linePosition = linePosition
    var lineByteOffset = lineByteOffset
    var lastSuccessStateOccursCount = 0
    var lastMovedOffset = lineStartOffset

    val matchBeginString = lineStartOffset.offset == 0 && linePosition.offset == 0
    // makes sense only for a line, cannot be used across lines;
    // when the topmost begin match consumed the trailing newline of the previous line,
    // \G matches at the beginning of this line.
    var anchorByteOffset = if (checkWhileConditions && stackFrames.last().state.matchedEOL) 0.byteOffset() else (-1).byteOffset()

    return mySyntaxMatcher.matchingString(line) { string ->
      if (checkWhileConditions) {
        // Check the while-conditions of the rules currently on the stack from the outermost to the innermost rule.
        // When a while-condition fails, its rule together with every rule
        // nested inside it is discarded from the stack, and the discarded frames take their scopes away with them.
        // The conditions of the discarded nested rules are not checked.
        val newStackFrames = persistentListOf<TextMateStackFrame>().builder()
        for (frame in stackFrames) {
          if (frame.state.syntaxRule.getStringAttribute(Constants.StringKey.WHILE) != null) {
            val matchWhile = mySyntaxMatcher.matchStringRegex(keyName = Constants.StringKey.WHILE,
                                                              string = string,
                                                              byteOffset = lineByteOffset,
                                                              matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                              matchBeginString = matchBeginString,
                                                              lexerState = frame.state,
                                                              checkCancelledCallback = checkCancelledCallback)
            if (matchWhile.matched) {
              newStackFrames.add(frame)
              if (frame.state.syntaxRule.getCaptureRules(Constants.CaptureKey.WHILE_CAPTURES) != null ||
                  frame.state.syntaxRule.getCaptureRules(Constants.CaptureKey.CAPTURES) != null) {
                val framesWithWhileRule = newStackFrames.build()
                parseCaptures(output, frame.scopes, Constants.CaptureKey.WHILE_CAPTURES, frame.state.syntaxRule, matchWhile, string, line,
                              lineStartOffset, framesWithWhileRule, checkCancelledCallback) ||
                  parseCaptures(output, frame.scopes, Constants.CaptureKey.CAPTURES, frame.state.syntaxRule, matchWhile, string, line,
                                lineStartOffset, framesWithWhileRule, checkCancelledCallback)
              }
              anchorByteOffset = matchWhile.byteRange().end
            }
            else {
              break
            }
          }
          else {
            newStackFrames.add(frame)
          }
        }
        stackFrames = newStackFrames.build()
      }

      var scopes = stackFrames.last().scopes
      val localStates = mutableSetOf<TextMateLexerState>()
      while (true) {
        val lastState = stackFrames.last().state
        val lastRule = lastState.syntaxRule

        val currentState = mySyntaxMatcher.matchRule(syntaxNodeDescriptor = lastRule,
                                                     string = string,
                                                     byteOffset = lineByteOffset,
                                                     matchBeginPosition = anchorByteOffset == lineByteOffset,
                                                     matchBeginString = matchBeginString,
                                                     priority = TextMateWeigh.Priority.NORMAL,
                                                     currentScope = scopes.currentScope,
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
          val poppedState = stackFrames.last().state
          if (poppedState.matchData.matched && !poppedState.matchedEOL) {
            // if begin hasn't matched EOL, it was performed on the same line; we need to use its anchor
            anchorByteOffset = poppedState.matchData.byteRange().end
          }
          stackFrames = stackFrames.removingAt(stackFrames.size - 1)

          val endRange = endMatch.charRange(string)
          endPosition = endRange.start
          val startPosition = endPosition
          scopes = closeScopeSelector(output, scopes, startPosition + lineStartOffset) // closing content scope
          if (lastRule.getCaptureRules(Constants.CaptureKey.END_CAPTURES) == null && lastRule.getCaptureRules(Constants.CaptureKey.CAPTURES) == null ||
              parseCaptures(output, scopes, Constants.CaptureKey.END_CAPTURES, lastRule, endMatch, string, line, lineStartOffset, stackFrames, checkCancelledCallback) ||
              parseCaptures(output, scopes, Constants.CaptureKey.CAPTURES, lastRule, endMatch, string, line, lineStartOffset, stackFrames, checkCancelledCallback)) {
            // move line position only if anything was captured or if there is nothing to capture at all
            endPosition = endRange.end
          }
          scopes = closeScopeSelector(output, scopes, endPosition + lineStartOffset) // closing basic scope

          if (linePosition == endPosition && containsLexerState(localStates, poppedState) && poppedState.enterByteOffset == lineByteOffset) {
            addToken(output, scopes.currentScope, lineLength + lineStartOffset)
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
            val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
            val scopesWithName = openScopeSelector(output, scopes, name, startPosition + lineStartOffset)

            // the captures are parsed with the new rule already on the stack;
            // the frame is added anew afterwards, with the content-name selector included in its scopes
            val statesWithCurrent = stackFrames.adding(TextMateStackFrame(currentState, scopesWithName))
            parseCaptures(output, scopesWithName, Constants.CaptureKey.BEGIN_CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, statesWithCurrent,
                          checkCancelledCallback) ||
              parseCaptures(output, scopesWithName, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, statesWithCurrent,
                            checkCancelledCallback)

            val contentName = getStringAttribute(Constants.StringKey.CONTENT_NAME, currentRule, string, currentMatch)
            scopes = openScopeSelector(output, scopesWithName, contentName, endPosition + lineStartOffset)
            stackFrames = stackFrames.adding(TextMateStackFrame(currentState, scopes))
          }
          else if (currentRule.getStringAttribute(Constants.StringKey.MATCH) != null) {
            val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
            val scopesWithName = openScopeSelector(output, scopes, name, startPosition + lineStartOffset)
            parseCaptures(output, scopesWithName, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, stackFrames,
                          checkCancelledCallback)
            closeScopeSelector(output, scopesWithName, endPosition + lineStartOffset)
          }

          if (linePosition == endPosition && containsLexerState(localStates, currentState)) {
            addToken(output, scopes.currentScope, lineLength + lineStartOffset)
            break
          }
          localStates.add(currentState)
        }
        else {
          addToken(output, scopes.currentScope, lineLength + lineStartOffset)
          break
        }

        // global looping protection
        if (lastMovedOffset < myCurrentOffset) {
          lastSuccessStackFrames = stackFrames
          lastSuccessStateOccursCount = 0
          lastMovedOffset = myCurrentOffset
        }
        else if (lastSuccessStackFrames == stackFrames) {
          if (lastSuccessStateOccursCount > MAX_LOOPS_COUNT) {
            addToken(output, scopes.currentScope, lineLength + lineStartOffset)
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
      stackFrames
    }
  }

  /**
   * The scope stack is only used to build the capture scopes upon:
   * whatever the captures open, they close before the function returns, so the caller's stack stays valid.
   */
  private fun parseCaptures(
    output: MutableList<TextmateToken>,
    scopes: TextMateScopeStack,
    captureKey: Constants.CaptureKey,
    rule: SyntaxNodeDescriptor,
    matchData: MatchData,
    string: TextMateString,
    line: CharSequence,
    startLineOffset: TextMateCharOffset,
    states: PersistentList<TextMateStackFrame>,
    checkCancelledCallback: Runnable?,
  ): Boolean {
    val captures = rule.getCaptureRules(captureKey) ?: return false

    var scopes = scopes
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
        scopes = closeScopeSelector(output, scopes, startLineOffset + activeCaptureRanges.removeLast().end)
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
          scopes = openScopeSelector(output, scopes, scopeName, startLineOffset + captureRange.start)
          activeCaptureRanges.addLast(captureRange)
        }
        else {
          while (indexOfSpace.offset >= 0) {
            scopes = openScopeSelector(output, scopes, scopeName.subSequence(selectorStartOffset, indexOfSpace), startLineOffset + captureRange.start)
            selectorStartOffset = TextMateCharOffset(indexOfSpace.offset + 1)
            indexOfSpace = scopeName.indexOf(char = ' ', startIndex = selectorStartOffset)
            activeCaptureRanges.addLast(captureRange)
          }
          scopes = openScopeSelector(output, scopes, scopeName.subSequence(selectorStartOffset, scopeName.length.charOffset()), startLineOffset + captureRange.start)
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
                    stackFrames = states.adding(TextMateStackFrame(captureState, scopes)),
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
      scopes = closeScopeSelector(output, scopes, startLineOffset + activeCaptureRanges.removeLast().end)
    }
    return true
  }

  private fun openScopeSelector(output: MutableList<TextmateToken>, scopes: TextMateScopeStack, name: CharSequence?, position: TextMateCharOffset): TextMateScopeStack {
    addToken(output, scopes.currentScope, position)
    return scopes.push(name)
  }

  private fun closeScopeSelector(output: MutableList<TextmateToken>, scopes: TextMateScopeStack, position: TextMateCharOffset): TextMateScopeStack {
    val lastOpenedName = scopes.currentScope.scopeName
    if (!lastOpenedName.isNullOrEmpty()) {
      addToken(output, scopes.currentScope, position)
    }
    return scopes.pop()
  }

  private fun addToken(output: MutableList<TextmateToken>, currentScope: TextMateScope, position: TextMateCharOffset) {
    val position = min(position.offset, myText.length).charOffset()
    if (position > myCurrentOffset) {
      var restartable = currentScope.parent == null
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
        output.add(TextmateToken(scope = currentScope,
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

/**
 * An entry of the lexer rule stack: the [state] of an entered rule and the scope stack [scopes]
 * as of entering it, with the rule's name and content-name selectors included.
 * Since [TextMateScopeStack] is immutable, the scopes stay valid for as long as the frame is on the stack,
 * so the scope stack at the beginning of a line is `states.last().scopes` — nothing is carried
 * between lines besides the frames themselves.
 *
 * Equality is defined by [state] alone ([scopes] are derived from the states of the frames below on the stack):
 * the looping protection in [TextMateLexerCore.parseLine] relies on it when comparing stack snapshots.
 */
private class TextMateStackFrame(val state: TextMateLexerState, val scopes: TextMateScopeStack) {
  override fun equals(other: Any?): Boolean {
    return this === other || other is TextMateStackFrame && state == other.state
  }

  override fun hashCode(): Int {
    return state.hashCode()
  }
}

/**
 * An immutable stack of scope selectors.
 * [currentScope] is the concatenation of the scope names of all pushed selectors.
 * Each stack node corresponds to one pushed selector: a selector like `foo bar` contributes
 * several scope names, but still one stack node, so popping it drops all its names at once.
 */
private class TextMateScopeStack private constructor(
  val currentScope: TextMateScope,
  private val parent: TextMateScopeStack?,
) {
  constructor(rootScope: TextMateScope) : this(rootScope, null)

  fun push(name: CharSequence?): TextMateScopeStack {
    var scope = currentScope
    var prevIndexOfSpace = 0
    if (name != null) {
      var indexOfSpace = name.indexOf(char = ' ', startIndex = 0, ignoreCase = false)
      while (indexOfSpace >= 0) {
        scope = scope.add(name.subSequence(prevIndexOfSpace, indexOfSpace))
        prevIndexOfSpace = indexOfSpace + 1
        indexOfSpace = name.indexOf(char = ' ', startIndex = prevIndexOfSpace, ignoreCase = false)
      }
    }
    scope = scope.add(name?.subSequence(prevIndexOfSpace, name.length))
    return TextMateScopeStack(scope, this)
  }

  fun pop(): TextMateScopeStack {
    return parent ?: this
  }
}