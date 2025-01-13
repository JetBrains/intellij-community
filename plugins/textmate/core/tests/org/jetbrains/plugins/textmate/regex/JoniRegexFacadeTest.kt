package org.jetbrains.plugins.textmate.regex

import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory

class JoniRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return JoniRegexFactory().regex(s)
  }
}