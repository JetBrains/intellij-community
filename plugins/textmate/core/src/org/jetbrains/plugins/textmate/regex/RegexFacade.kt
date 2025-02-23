package org.jetbrains.plugins.textmate.regex

import kotlinx.coroutines.Runnable

interface RegexFacade {
  fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData

  fun match(string: TextMateString,
            byteOffset: Int,
            matchBeginPosition: Boolean,
            matchBeginString: Boolean,
            checkCancelledCallback: Runnable?): MatchData
}

interface RegexFactory {
  fun regex(pattern: CharSequence): RegexFacade

  fun string(string: CharSequence): TextMateString
}