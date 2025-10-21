package com.intellij.textmate.joni

import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RegexProvider

class JoniRegexFacadeTest : RegexFacadeTest() {
  override fun <T> withRegexProvider(body: (RegexProvider) -> T): T {
    return body(DefaultRegexProvider(JoniRegexFactory()))
  }
}