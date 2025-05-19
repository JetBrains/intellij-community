package com.intellij.textmate.joni

import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.TextMateString
import org.jetbrains.plugins.textmate.regex.TextMateStringImpl

class JoniRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return JoniRegexFactory().regex(s)
  }

  override fun string(s: String): TextMateString {
    return JoniRegexFactory().string(s)
  }
}