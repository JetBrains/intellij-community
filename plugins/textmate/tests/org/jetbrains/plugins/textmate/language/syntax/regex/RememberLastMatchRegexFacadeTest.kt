package org.jetbrains.plugins.textmate.language.syntax.regex

import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory

class RememberLastMatchRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return RememberingLastMatchRegexFactory(JoniRegexFactory()).regex(s)
  }
}