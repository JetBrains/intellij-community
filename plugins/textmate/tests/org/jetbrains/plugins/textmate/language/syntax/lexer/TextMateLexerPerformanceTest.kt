package org.jetbrains.plugins.textmate.language.syntax.lexer

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.findScopeByFileName
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable
import org.jetbrains.plugins.textmate.loadBundle
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class TextMateLexerPerformanceTest : UsefulTestCase() {
  fun testPerformance() {
    val syntaxTable = TextMateSyntaxTable()
    val matchers = syntaxTable.loadBundle(TestUtil.JAVA)
    val myFile = File(PlatformTestUtil.getCommunityPath() + "/platform/platform-impl/src/com/intellij/openapi/editor/impl/EditorImpl.java")
    syntaxTable.compact()

    val scopeName = findScopeByFileName(matchers, myFile.name)
    val text = StringUtil.convertLineSeparators(FileUtil.loadFile(myFile, StandardCharsets.UTF_8))

    Benchmark.newBenchmark("bench") {
      val lexer = TextMateHighlightingLexer(TextMateLanguageDescriptor(scopeName, syntaxTable.getSyntax(scopeName)),
                                            JoniRegexFactory(),
                                            -1)
      lexer.start(text)
      while (lexer.tokenType != null) {
        lexer.advance()
      }
    }.start()
  }
}