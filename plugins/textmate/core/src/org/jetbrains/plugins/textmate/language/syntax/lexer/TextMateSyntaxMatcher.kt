package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.RegexFactory
import org.jetbrains.plugins.textmate.regex.RegexProvider
import org.jetbrains.plugins.textmate.regex.TextMateByteOffset
import org.jetbrains.plugins.textmate.regex.TextMateString

interface TextMateSyntaxMatcher {
  fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    injections: List<InjectionNodeDescriptor>,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState

  fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    lexerState: TextMateLexerState,
    checkCancelledCallback: Runnable?,
  ): MatchData

  fun <T> matchingString(s: CharSequence, body: (TextMateString) -> T): T
}

class TextMateSyntaxMatcherImpl(
  private val regexProvider: RegexProvider,
  private val mySelectorWeigher: TextMateSelectorWeigher,
) : TextMateSyntaxMatcher {

  @Deprecated("Use TextMateSyntaxMatcherImpl(RegexFactory, TextMateSelectorWeigher)")
  constructor(regexFactory: RegexFactory,
              weigher: TextMateSelectorWeigher) : this(DefaultRegexProvider(regexFactory), weigher)

  override fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    injections: List<InjectionNodeDescriptor>,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState {
    var resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor)
    val children = syntaxNodeDescriptor.children
    for (child in children) {
      resultState = moreImportantState(resultState,
                                       matchFirstChild(syntaxNodeDescriptor = child,
                                                       string = string,
                                                       byteOffset = byteOffset,
                                                       matchBeginPosition = matchBeginPosition,
                                                       matchBeginString = matchBeginString,
                                                       priority = priority,
                                                       injections = injections,
                                                       currentScope = currentScope))
      if (resultState.matchData.matched && resultState.matchData.byteRange().start == byteOffset) {
        // Optimization. There cannot be anything more `important` than the current state matched from the very beginning
        break
      }
    }
    return moreImportantState(resultState, matchInjections(syntaxNodeDescriptor = syntaxNodeDescriptor,
                                                           string = string,
                                                           byteOffset = byteOffset,
                                                           matchBeginPosition = matchBeginPosition,
                                                           matchBeginString = matchBeginString,
                                                           currentScope = currentScope,
                                                           injections = injections,
                                                           checkCancelledCallback = checkCancelledCallback))
  }

  override fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    lexerState: TextMateLexerState,
    checkCancelledCallback: Runnable?,
  ): MatchData {
    val regex = lexerState.syntaxRule.getStringAttribute(keyName)
    if (regex.isNullOrEmpty()) return MatchData.NOT_MATCHED
    val regexString = if (lexerState.syntaxRule.hasBackReference(keyName)) {
      SyntaxMatchUtils.replaceGroupsWithMatchDataInRegex(regex, lexerState.string, lexerState.matchData)
    }
    else {
      regex
    }
    return regexProvider.withRegex(regexString) { regexFacade ->
      regexFacade.match(string = string,
                        byteOffset = byteOffset,
                        matchBeginPosition = matchBeginPosition,
                        matchBeginString = matchBeginString,
                        checkCancelledCallback = checkCancelledCallback)
    }
  }

  override fun <T> matchingString(s: CharSequence, body: (TextMateString) -> T): T {
    return regexProvider.withString(s, body)
  }

  private fun matchFirstChild(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    injections: List<InjectionNodeDescriptor>,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable? = null,
  ): TextMateLexerState {
    val match = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.MATCH)
    if (match != null) {
      return if (match.isEmpty()) {
        TextMateLexerState.notMatched(syntaxNodeDescriptor)
      }
      else {
        regexProvider.withRegex(match) { regex ->
          val matchData = regex.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback)
          TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string)
        }
      }
    }
    val begin = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.BEGIN)
    if (begin != null) {
      return if (begin.isEmpty()) {
        TextMateLexerState.notMatched(syntaxNodeDescriptor)
      }
      else {
        regexProvider.withRegex(begin) { regex ->
          val matchData = regex.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback)
          TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string)
        }
      }
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.END) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor)
    }
    return matchRule(syntaxNodeDescriptor, string, byteOffset, matchBeginPosition, matchBeginString, priority, currentScope,
                     injections, checkCancelledCallback)
  }

  private fun hasBeginKey(lexerState: TextMateLexerState): Boolean {
    return lexerState.syntaxRule.getStringAttribute(Constants.StringKey.BEGIN) != null
  }

  private fun moreImportantState(oldState: TextMateLexerState, newState: TextMateLexerState): TextMateLexerState {
    if (!newState.matchData.matched) {
      return oldState
    }
    else if (!oldState.matchData.matched) {
      return newState
    }
    val newScore = newState.matchData.byteRange().start
    val oldScore = oldState.matchData.byteRange().start
    if (newScore < oldScore || newScore == oldScore && newState.priorityMatch > oldState.priorityMatch) {
      if (!newState.matchData.byteRange().isEmpty || oldState.matchData.byteRange().isEmpty || hasBeginKey(newState)) {
        return newState
      }
    }
    return oldState
  }

  private fun matchInjections(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: TextMateByteOffset,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    currentScope: TextMateScope,
    injections: List<InjectionNodeDescriptor>,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState {
    var resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor)

    for (injection in injections) {
      val selectorWeigh = mySelectorWeigher.weigh(injection.selector, currentScope)
      if (selectorWeigh.weigh <= 0) {
        continue
      }
      val injectionState =
        matchRule(
          syntaxNodeDescriptor = injection.syntaxNodeDescriptor,
          string = string,
          byteOffset = byteOffset,
          matchBeginPosition = matchBeginPosition,
          matchBeginString = matchBeginString,
          priority = selectorWeigh.priority,
          currentScope = currentScope,
          injections = emptyList(),
          checkCancelledCallback = checkCancelledCallback
        )

      resultState = moreImportantState(resultState, injectionState)
    }

    return resultState
  }
}
