package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.CachingRegexFactory
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest

class CachingRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return CachingRegexFactory(JoniRegexFactory()).regex(s)
  }
}