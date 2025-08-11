package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RegexProvider

class CaffeineCachingRegexFacadeTest : RegexFacadeTest() {
  override fun <T> withRegexProvider(body: (RegexProvider) -> T): T {
    return body(CaffeineCachingRegexProvider(JoniRegexFactory()))
  }
}