package org.jetbrains.plugins.textmate.language.syntax.regex

import org.jetbrains.plugins.textmate.regex.CachingRegexFactory
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory

class CachingRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return CachingRegexFactory(JoniRegexFactory()).regex(s)
  }
}