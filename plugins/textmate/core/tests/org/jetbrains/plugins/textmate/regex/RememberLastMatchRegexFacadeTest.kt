package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory

class RememberLastMatchRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return RememberingLastMatchRegexFactory(JoniRegexFactory()).regex(s)
  }
}