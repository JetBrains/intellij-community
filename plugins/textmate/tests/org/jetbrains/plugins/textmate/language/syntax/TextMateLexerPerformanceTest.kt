package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.textmate.joni.JoniRegexFactory
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.TestUtil.findScopeByFileName
import org.jetbrains.plugins.textmate.TestUtil.loadBundle
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.lexer.*
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import org.jetbrains.plugins.textmate.regex.cachingRegexProvider
import java.io.File
import java.nio.charset.StandardCharsets

class TextMateLexerPerformanceTest : UsefulTestCase() {
  fun testPerformance() {
    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    val weigher = TextMateSelectorCachingWeigher(TextMateSelectorWeigherImpl())
    doPerformanceTest(TextMateCachingSyntaxMatcher(TextMateSyntaxMatcherImpl(regexProvider, weigher)))
  }

  fun testPerformanceCaffeineRegexFactoryCache() {
    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    val weigher = TextMateSelectorCachingWeigher(TextMateSelectorWeigherImpl())
    doPerformanceTest(TextMateSyntaxMatcherImpl(regexProvider, weigher))
  }

  fun testPerformanceSlruCache() {
    RememberingLastMatchRegexFactory(JoniRegexFactory()).cachingRegexProvider().use { regexProvider ->
      TextMateSelectorWeigherImpl().caching().use { weigher ->
        TextMateSyntaxMatcherImpl(regexProvider, weigher).caching().use { syntaxMatcher ->
          doPerformanceTest(syntaxMatcher)
        }
      }
    }
  }

  fun testPerformanceRegexFactorySlruCache() {
    RememberingLastMatchRegexFactory(JoniRegexFactory()).cachingRegexProvider().use { regexProvider ->
      TextMateSelectorWeigherImpl().caching().use { weigher ->
        doPerformanceTest(TextMateSyntaxMatcherImpl(regexProvider, weigher))
      }
    }
  }

  fun testPerformanceNoCache() {
    val regexProvider = DefaultRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    TextMateSelectorWeigherImpl().caching().use { weigher ->
      doPerformanceTest(TextMateSyntaxMatcherImpl(regexProvider, weigher))
    }
  }

  private fun doPerformanceTest(syntaxMatcher: TextMateSyntaxMatcher) {
    val builder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val matchers = builder.loadBundle(TestUtil.JAVA)
    val myFile = File(PlatformTestUtil.getCommunityPath() + "/platform/platform-impl/src/com/intellij/openapi/editor/impl/EditorImpl.java")
    val syntaxTable = builder.build()

    val scopeName = findScopeByFileName(matchers, myFile.name)
    val text = StringUtil.convertLineSeparators(FileUtil.loadFile(myFile, StandardCharsets.UTF_8))

    Benchmark.newBenchmark(getTestName(true)) {
      val lexer = TextMateHighlightingLexer(syntaxTable.getLanguageDescriptor(scopeName), syntaxMatcher, -1)
      lexer.start(text)
      while (lexer.tokenType != null) {
        lexer.advance()
      }
    }.start()
  }
}