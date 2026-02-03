package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RegexProvider
import org.jetbrains.plugins.textmate.regex.cachingRegexProvider

class CachingRegexFacadeTest : RegexFacadeTest() {
  override fun <T> withRegexProvider(body: (RegexProvider) -> T): T {
    return JoniRegexFactory().cachingRegexProvider().use(body)
  }
}