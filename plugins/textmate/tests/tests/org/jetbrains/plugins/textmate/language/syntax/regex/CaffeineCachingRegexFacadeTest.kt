package org.jetbrains.plugins.textmate.language.syntax.regex

import com.intellij.textmate.joni.JoniRegexFactory
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RegexFacade
import org.jetbrains.plugins.textmate.regex.RegexFacadeTest
import org.jetbrains.plugins.textmate.regex.RegexProvider
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CaffeineCachingRegexFacadeTest : RegexFacadeTest() {
  override fun <T> withRegexProvider(body: (RegexProvider) -> T): T {
    return body(CaffeineCachingRegexProvider(JoniRegexFactory()))
  }

  @Test
  fun `the same pattern gives back the same facade`() {
    val provider = CaffeineCachingRegexProvider(JoniRegexFactory())
    val first = provider.withRegex("[0-9]+") { it }
    val second = provider.withRegex("[0-9]+") { it }
    assertSame(first, second)
  }

  @Test
  fun `a different pattern gives back a different facade`() {
    val provider = CaffeineCachingRegexProvider(JoniRegexFactory())
    val digits: RegexFacade = provider.withRegex("[0-9]+") { it }
    val letters: RegexFacade = provider.withRegex("[a-z]+") { it }
    assertNotSame(digits, letters)
  }
}
