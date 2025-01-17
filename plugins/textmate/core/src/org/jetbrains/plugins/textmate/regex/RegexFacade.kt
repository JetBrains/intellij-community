package org.jetbrains.plugins.textmate.regex

interface RegexFacade {
  fun match(string: TextMateString, checkCancelledCallback: Runnable?): MatchData

  fun match(string: TextMateString,
            byteOffset: Int,
            matchBeginPosition: Boolean,
            matchBeginString: Boolean,
            checkCancelledCallback: Runnable?): MatchData
}

interface RegexFactory {
  fun regex(regexString: String): RegexFacade

  fun string(string: CharSequence): TextMateString
}