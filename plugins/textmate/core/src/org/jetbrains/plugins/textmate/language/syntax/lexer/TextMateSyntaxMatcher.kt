package org.jetbrains.plugins.textmate.language.syntax.lexer

import kotlinx.coroutines.Runnable
import org.jetbrains.plugins.textmate.Constants
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh
import org.jetbrains.plugins.textmate.regex.MatchData
import org.jetbrains.plugins.textmate.regex.RegexFactory
import org.jetbrains.plugins.textmate.regex.TextMateString

interface TextMateSyntaxMatcher {
  fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState

  fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    lexerState: TextMateLexerState,
    checkCancelledCallback: Runnable?,
  ): MatchData

  fun createStringToMatch(s: CharSequence): TextMateString
}

class TextMateSyntaxMatcherImpl(
  private val regexFactory: RegexFactory,
  private val mySelectorWeigher: TextMateSelectorWeigher,
) : TextMateSyntaxMatcher {

  override fun matchRule(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
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
                                                       currentScope = currentScope))
      if (resultState.matchData.matched && resultState.matchData.byteOffset().start == byteOffset) {
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
                                                           checkCancelledCallback = checkCancelledCallback))
  }

  override fun matchStringRegex(
    keyName: Constants.StringKey,
    string: TextMateString,
    byteOffset: Int,
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
    return regexFactory.regex(regexString).match(string = string,
                                                 byteOffset = byteOffset,
                                                 matchBeginPosition = matchBeginPosition,
                                                 matchBeginString = matchBeginString,
                                                 checkCancelledCallback = checkCancelledCallback)
  }

  override fun createStringToMatch(s: CharSequence): TextMateString {
    return regexFactory.string(s)
  }

  private fun matchFirstChild(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    priority: TextMateWeigh.Priority,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable? = null,
  ): TextMateLexerState {
    val match = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.MATCH)
    if (match != null) {
      return if (match.isEmpty()) {
        TextMateLexerState.notMatched(syntaxNodeDescriptor)
      }
      else {
        val regex = regexFactory.regex(match)
        val matchData = regex.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback)
        return TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string)
      }
    }
    val begin = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.BEGIN)
    if (begin != null) {
      if (begin.isEmpty()) {
        return TextMateLexerState.notMatched(syntaxNodeDescriptor)
      }
      else {
        val regex = regexFactory.regex(begin)
        val matchData = regex.match(string, byteOffset, matchBeginPosition, matchBeginString, checkCancelledCallback)
        return TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string)
      }
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.END) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor)
    }
    return matchRule(syntaxNodeDescriptor, string, byteOffset, matchBeginPosition, matchBeginString, priority, currentScope,
                     checkCancelledCallback)
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
    val newScore = newState.matchData.byteOffset().start
    val oldScore = oldState.matchData.byteOffset().start
    if (newScore < oldScore || newScore == oldScore && newState.priorityMatch > oldState.priorityMatch) {
      if (!newState.matchData.byteOffset().isEmpty || oldState.matchData.byteOffset().isEmpty || hasBeginKey(newState)) {
        return newState
      }
    }
    return oldState
  }

  private fun matchInjections(
    syntaxNodeDescriptor: SyntaxNodeDescriptor,
    string: TextMateString,
    byteOffset: Int,
    matchBeginPosition: Boolean,
    matchBeginString: Boolean,
    currentScope: TextMateScope,
    checkCancelledCallback: Runnable?,
  ): TextMateLexerState {
    var resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor)
    val injections = syntaxNodeDescriptor.injections

    for (injection in injections) {
      val selectorWeigh = mySelectorWeigher.weigh(injection.selector, currentScope)
      if (selectorWeigh.weigh <= 0) {
        continue
      }
      val injectionState: TextMateLexerState? =
        matchRule(injection.syntaxNodeDescriptor, string, byteOffset, matchBeginPosition, matchBeginString, selectorWeigh.priority,
                  currentScope, checkCancelledCallback)
      resultState = moreImportantState(resultState, injectionState!!)
    }
    return resultState
  }
}