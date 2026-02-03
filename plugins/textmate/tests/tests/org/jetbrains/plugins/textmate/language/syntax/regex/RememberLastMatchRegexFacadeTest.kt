package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory

class RememberLastMatchRegexFacadeTest : RegexFacadeTest() {
  override fun <T> withRegexProvider(body: (RegexProvider) -> T): T {
    return body(DefaultRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory())))
  }
}