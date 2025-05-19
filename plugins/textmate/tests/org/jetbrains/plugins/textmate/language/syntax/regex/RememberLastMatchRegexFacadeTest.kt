package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import org.jetbrains.plugins.textmate.regex.TextMateString

class RememberLastMatchRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return RememberingLastMatchRegexFactory(JoniRegexFactory()).regex(s)
  }

  override fun string(s: String): TextMateString {
    return JoniRegexFactory().string(s)
  }
}