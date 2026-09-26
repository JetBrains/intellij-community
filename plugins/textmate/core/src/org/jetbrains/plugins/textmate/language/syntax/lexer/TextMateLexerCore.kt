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
    myStackFrames = rootContinuation().frames
  }

  /**
   * Restarts the lexer at [startCharOffset] with the rule stack kept in [continuation].
   *
   * [startCharOffset] must be the beginning of a line. [advanceLine] treats the current offset as a line start,
   * and [parseLine] derives the `^`, `\A` and `\G` anchors from it.
   */
  fun init(text: CharSequence, startCharOffset: Int, continuation: TextMateLexerContinuation) {
    myText = text
    myCurrentOffset = startCharOffset.charOffset()
    myStackFrames = continuation.frames
  }

  /**
   * The rule stack a file starts with. [TextMateLexerContinuation.equals] reports it equal to any other
   * root continuation of the same language.
   */
  fun rootContinuation(): TextMateLexerContinuation {
    val rootFrame = TextMateStackFrame(
      state = TextMateLexerState.notMatched(syntaxRule = languageDescriptor.rootSyntaxNode),
      scopes = TextMateScopeStack(rootScope = TextMateScope(languageDescriptor.rootScopeName, null))
    )
    return TextMateLexerContinuation(persistentListOf(rootFrame))
  }

  /**
   * The rule stack at [getCurrentOffset]. Give it back to [init] to continue the file from this offset.
   */
  fun getContinuation(): TextMateLexerContinuation {
    return TextMateLexerContinuation(myStackFrames)
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
          val matchBeginString = lineStartOffset.offset == 0 && linePosition.offset == 0
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
              if (matchWhile.byteRange().end > lineByteOffset) {
                linePosition = matchWhile.charRange(string).end
                lineByteOffset = matchWhile.byteRange().end
              }
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
      val pushedAnchors = mutableMapOf<Int, TextMateByteOffset>()
      while (true) {
        val matchBeginString = lineStartOffset.offset == 0 && linePosition.offset == 0
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
        if (endMatch.matched && (!currentMatch.matched || endWinsOverCurrent(applyEndPatternLast, currentState, endMatch) || lastState == currentState)) {
          val poppedFrame = stackFrames.last()
          val poppedState = poppedFrame.state
          anchorByteOffset = pushedAnchors.remove(stackFrames.size - 1) ?: (-1).byteOffset()
          stackFrames = stackFrames.removingAt(stackFrames.size - 1)

          val endRange = endMatch.charRange(string)
          scopes = closeScopeSelector(output, scopes, endRange.start + lineStartOffset) // closing content scope
          // `captures` apply to the end match only when the rule has no dedicated `endCaptures`
          parseCaptures(output, scopes, Constants.CaptureKey.END_CAPTURES, lastRule, endMatch, string, line, lineStartOffset, stackFrames, checkCancelledCallback) ||
            parseCaptures(output, scopes, Constants.CaptureKey.CAPTURES, lastRule, endMatch, string, line, lineStartOffset, stackFrames, checkCancelledCallback)
          endPosition = endRange.end
          scopes = closeScopeSelector(output, scopes, endPosition + lineStartOffset) // closing basic scope

          if (linePosition == endPosition && containsLexerState(localStates, poppedState) && poppedState.enterByteOffset == lineByteOffset) {
            // the grammar pushed and popped a rule without advancing; assume that was a mistake
            // and continue the line in the rule's state
            stackFrames = stackFrames.adding(poppedFrame)
            addToken(output, poppedFrame.scopes.currentScope, lineLength + lineStartOffset)
            break
          }
          localStates.remove(poppedState)
        }
        else if (currentMatch.matched) {
          val currentRange = currentMatch.charRange(string)
          val startPosition = currentRange.start
          endPosition = currentRange.end

          if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
            // only a begin match moves the \G anchor; a plain match rule keeps it intact
            pushedAnchors[stackFrames.size] = anchorByteOffset
            anchorByteOffset = currentMatch.byteRange().end
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
            val textDerived = currentState.capturedTexts != null ||
                              currentRule.hasBackReference(Constants.StringKey.NAME) ||
                              currentRule.hasBackReference(Constants.StringKey.CONTENT_NAME)
            stackFrames = stackFrames.adding(TextMateStackFrame(currentState, scopes, textDerived))
          }
          else if (currentRule.getStringAttribute(Constants.StringKey.MATCH) != null) {
            val name = getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch)
            val scopesWithName = openScopeSelector(output, scopes, name, startPosition + lineStartOffset)
            parseCaptures(output, scopesWithName, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, stackFrames,
                          checkCancelledCallback)
            closeScopeSelector(output, scopesWithName, endPosition + lineStartOffset)
          }

          if (linePosition == endPosition && containsLexerState(localStates, currentState)) {
            if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
              // the grammar pushed the same rule again without advancing;
              // revert the push before stopping the line, so the stack doesn't grow
              stackFrames = stackFrames.removingAt(stackFrames.size - 1)
              pushedAnchors.remove(stackFrames.size)
              scopes = stackFrames.last().scopes
            }
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
     * when it matches strictly before the nested pattern; otherwise it also wins the ties,
     * unless the nested match comes from a left-injection (`L:`) whose priority beats the `end`
     * pattern on ties.
     */
    private fun endWinsOverCurrent(applyEndPatternLast: Boolean, currentState: TextMateLexerState, endMatch: MatchData): Boolean {
      val currentStart = currentState.matchData.byteRange().start
      val endStart = endMatch.byteRange().start
      return when {
        currentStart != endStart -> currentStart > endStart
        currentState.priorityMatch > TextMateWeigh.Priority.NORMAL -> false
        else -> !applyEndPatternLast
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
internal class TextMateStackFrame(
  val state: TextMateLexerState,
  val scopes: TextMateScopeStack,
  /**
   * True when the frame holds text that the lexer matched in the document, and not only the grammar.
   * The captured texts of a back-reference are such a text, and so is a scope name that a back-reference
   * builds. An edit makes a new text, so a frame like this cannot occur again after the edit,
   * see [TextMateLexerContinuation.isStable].
   */
  val textDerived: Boolean = state.capturedTexts != null,
) {
  override fun equals(other: Any?): Boolean {
    return this === other || other is TextMateStackFrame && state == other.state
  }

  override fun hashCode(): Int {
    return state.hashCode()
  }

  /**
   * Compares the part of the frame that decides how the following lines are lexed.
   *
   * [equals] cannot serve here. [TextMateLexerState.equals] includes the identity of the line the rule
   * matched on, so two frames built by two runs over the same text are never equal. A continuation must
   * stay equal across runs, otherwise the editor highlighter never re-synchronises after an edit.
   * [TextMateLexerState.enterByteOffset] and the raw match offsets stay out as well. They are per-line loop
   * protection. [TextMateLexerCore.parseLine] reads them only for a frame that the same line pushed, so they
   * never decide how a later line is lexed. They must stay out. An edit before the rule moves them, and every
   * line below the edit would then get a new state.
   */
  fun continuationEquals(other: TextMateStackFrame): Boolean {
    return state.syntaxRule == other.state.syntaxRule &&
           state.matchedEOL == other.state.matchedEOL &&
           state.capturedTexts == other.state.capturedTexts &&
           scopes == other.scopes
  }

  fun continuationHashCode(): Int {
    var result = state.syntaxRule.hashCode()
    result = 31 * result + state.matchedEOL.hashCode()
    result = 31 * result + state.capturedTexts.hashCode()
    result = 31 * result + scopes.hashCode()
    return result
  }
}

/**
 * An immutable snapshot of the lexer rule stack at a line boundary.
 *
 * Two continuations are equal when they continue a file the same way, whatever line or lexer run they
 * come from. [org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexerCore.getContinuation]
 * produces one, and `TextMateLexerCore.init` consumes one.
 */
class TextMateLexerContinuation internal constructor(
  internal val frames: PersistentList<TextMateStackFrame>
) {
  private val hashCode: Int = frames.fold(1) { acc, frame -> 31 * acc + frame.continuationHashCode() }

  /**
   * True when a later lexer run over the same text can produce this continuation again.
   * A frame keeps the scopes of the frames below it, so one [TextMateStackFrame.textDerived] frame
   * makes the whole stack depend on the text the lexer matched, and an edit then makes a new continuation.
   * A caller that keeps a continuation for later use must keep a stable one only.
   */
  val isStable: Boolean = frames.none { it.textDerived }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextMateLexerContinuation) return false
    if (hashCode != other.hashCode || frames.size != other.frames.size) return false
    for (i in frames.indices) {
      if (!frames[i].continuationEquals(other.frames[i])) return false
    }
    return true
  }

  override fun hashCode(): Int {
    return hashCode
  }
}

/**
 * An immutable stack of scope selectors.
 * [currentScope] is the concatenation of the scope names of all pushed selectors.
 * Each stack node corresponds to one pushed selector: a selector like `foo bar` contributes
 * several scope names, but still one stack node, so popping it drops all its names at once.
 */
internal class TextMateScopeStack private constructor(
  val currentScope: TextMateScope,
  private val parent: TextMateScopeStack?,
) {
  constructor(rootScope: TextMateScope) : this(rootScope, null)

  private val depth: Int = (parent?.depth ?: 0) + 1

  /**
   * Compares the whole chain, not only [currentScope]. Two stacks can share a [currentScope] and still
   * pop differently: the selector `a b` makes one node with two names, while `a` then `b` makes two nodes.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextMateScopeStack || depth != other.depth) return false
    var left: TextMateScopeStack? = this
    var right: TextMateScopeStack? = other
    while (left !== right) {
      if (left == null || right == null || left.currentScope != right.currentScope) return false
      left = left.parent
      right = right.parent
    }
    return true
  }

  override fun hashCode(): Int {
    return 31 * depth + currentScope.hashCode()
  }

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