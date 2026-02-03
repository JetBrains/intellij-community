package org.jetbrains.plugins.textmate.regex

interface RegexProvider {
  fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T

  fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T
}

class DefaultRegexProvider(private val regexFactory: RegexFactory) : RegexProvider {
  override fun <T> withRegex(pattern: CharSequence, body: (RegexFacade) -> T): T {
    return regexFactory.regex(pattern).use(body)
  }

  override fun <T> withString(string: CharSequence, body: (TextMateString) -> T): T {
    return regexFactory.string(string).use(body)
  }
}