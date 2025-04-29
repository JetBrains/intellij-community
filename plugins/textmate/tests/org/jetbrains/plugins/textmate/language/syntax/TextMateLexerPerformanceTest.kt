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
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateCachingSyntaxMatcher
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.regex.CachingRegexFactory
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import java.io.File
import java.nio.charset.StandardCharsets

class TextMateLexerPerformanceTest : UsefulTestCase() {
  fun testPerformance() {
    val builder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val matchers = builder.loadBundle(TestUtil.JAVA)
    val myFile = File(PlatformTestUtil.getCommunityPath() + "/platform/platform-impl/src/com/intellij/openapi/editor/impl/EditorImpl.java")
    val syntaxTable = builder.build()

    val scopeName = findScopeByFileName(matchers, myFile.name)
    val text = StringUtil.convertLineSeparators(FileUtil.loadFile(myFile, StandardCharsets.UTF_8))

    Benchmark.newBenchmark("bench") {
      val regexFactory = CachingRegexFactory(RememberingLastMatchRegexFactory(JoniRegexFactory ()))
      val weigher = TextMateSelectorCachingWeigher(TextMateSelectorWeigherImpl())
      val syntaxMatcher = TextMateCachingSyntaxMatcher(TextMateSyntaxMatcherImpl(regexFactory, weigher))
      val lexer = TextMateHighlightingLexer(TextMateLanguageDescriptor(scopeName, syntaxTable.getSyntax(scopeName)),
                                            syntaxMatcher,
                                            -1)
      lexer.start(text)
      while (lexer.tokenType != null) {
        lexer.advance()
      }
    }.start()
  }
}