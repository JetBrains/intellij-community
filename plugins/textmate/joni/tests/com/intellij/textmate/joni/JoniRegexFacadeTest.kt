package com.intellij.textmate.joni

import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest

class JoniRegexFacadeTest : RegexFacadeTest() {
  override fun regex(s: String): RegexFacade {
    return JoniRegexFactory().regex(s)
  }
}