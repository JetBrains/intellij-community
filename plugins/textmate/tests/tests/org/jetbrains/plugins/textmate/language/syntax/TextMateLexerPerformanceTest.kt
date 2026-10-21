package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.textmate.joni.JoniRegexFactory
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.plugins.textmate.TestUtil
import org.jetbrains.plugins.textmate.TestUtil.findScopeByFileName
import org.jetbrains.plugins.textmate.TestUtil.loadBundle
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateCachingSyntaxMatcher
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcher
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.lexer.caching
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.DefaultRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import org.jetbrains.plugins.textmate.regex.cachingRegexProvider
import java.io.File
import java.nio.charset.StandardCharsets

@PerformanceUnitTest
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

  /**
   * The PowerShell case from IJPL-211694: the whole body sits inside one scriptblock, so the rule
   * stack stays deep for the whole file and every line pays the full child scan of the top rule.
   *
   * The matcher is wired exactly as `TextMateSyntaxHighlighterFactory` wires it, so the number
   * tracks what the editor does. The other cases here compare the caches against each other and
   * use other combinations on purpose.
   */
  fun testPerformancePowerShell() {
    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    TextMateSelectorWeigherImpl().caching().use { weigher ->
      TextMateSyntaxMatcherImpl(regexProvider, weigher).caching().use { syntaxMatcher ->
        doPerformanceTest(syntaxMatcher = syntaxMatcher,
                          bundleName = TestUtil.POWERSHELL,
                          fileName = "demo.ps1",
                          text = powerShellSingleScriptBlock())
      }
    }
  }

  /**
   * The minified-file shape: a few long lines, each of thousands of short statements.
   * The case is sensitive to the per-step costs that grow with the line length.
   * The match-rule cache key hashes the whole line on every step.
   * Every token converts a byte range to char offsets with a scan from the line start.
   *
   * The matcher is wired as `TextMateSyntaxHighlighterFactory` wires it, like [testPerformancePowerShell].
   */
  fun testPerformanceLongLines() {
    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))
    TextMateSelectorWeigherImpl().caching().use { weigher ->
      TextMateSyntaxMatcherImpl(regexProvider, weigher).caching().use { syntaxMatcher ->
        doPerformanceTest(syntaxMatcher = syntaxMatcher,
                          bundleName = TestUtil.POWERSHELL,
                          fileName = "demo.ps1",
                          text = powerShellLongLines())
      }
    }
  }

  private fun doPerformanceTest(syntaxMatcher: TextMateSyntaxMatcher) {
    val file = File(PlatformTestUtil.getCommunityPath() + "/platform/platform-impl/src/com/intellij/openapi/editor/impl/EditorImpl.java")
    doPerformanceTest(syntaxMatcher = syntaxMatcher,
                      bundleName = TestUtil.JAVA,
                      fileName = file.name,
                      text = StringUtil.convertLineSeparators(FileUtil.loadFile(file, StandardCharsets.UTF_8)))
  }

  private fun doPerformanceTest(
    syntaxMatcher: TextMateSyntaxMatcher,
    bundleName: String,
    fileName: String,
    text: String,
  ) {
    val builder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val matchers = builder.loadBundle(bundleName)
    val syntaxTable = builder.build()
    val scopeName = findScopeByFileName(matchers, fileName)

    Benchmark.newBenchmark(getTestName(true)) {
      val lexer = TextMateHighlightingLexer(syntaxTable.getLanguageDescriptor(scopeName), syntaxMatcher, -1)
      lexer.start(text)
      while (lexer.tokenType != null) {
        lexer.advance()
      }
    }.start()
  }

  /**
   * Builds a script whose whole body is nested in one function, in the shape of the file attached
   * to IJPL-211694. Each block repeats the same six lines with a different index, so the text is
   * long without being a single pathological line.
   */
  private fun powerShellSingleScriptBlock(): String {
    return buildString {
      append("function Invoke-DemoWorkload {\n")
      for (i in 1..BLOCKS) {
        append(BLOCK_TEMPLATE.replace(INDEX_MARKER, i.toString()))
      }
      append("}\n")
    }
  }

  /**
   * Builds [LONG_LINES] distinct lines of [STATEMENTS_PER_LINE] short statements each.
   * The lines are distinct, so the match-rule cache gives no cross-line hit,
   * as in a real minified file.
   */
  private fun powerShellLongLines(): String {
    return buildString {
      for (line in 1..LONG_LINES) {
        for (i in 1..STATEMENTS_PER_LINE) {
          append($$"""$v_$${line}_$$i = "item-$$line-$$i"; """)
        }
        append('\n')
      }
    }
  }

  companion object {
    private const val BLOCKS = 1200
    private const val LONG_LINES = 4
    private const val STATEMENTS_PER_LINE = 1000

    /** Stands for the index of the block. No PowerShell syntax in the template uses a tilde. */
    private const val INDEX_MARKER = "~i~"

    /**
     * One block of the generated script. The multi-dollar prefix keeps every `$` of the PowerShell
     * source literal, so the template reads as the script does.
     */
    private val BLOCK_TEMPLATE = $$"""
      $item~i~ = Get-ChildItem -Path "C:\logs\app-~i~.log" -Recurse -Force   # scan ~i~
      $map~i~ = @{ Name = 'demo-~i~'; Retries = ~i~; Enabled = $true; Ratio = ~i~.5 }
      if ($item~i~.Length -gt ~i~) { Write-Output "big: $($item~i~.Name) [$($map~i~.Retries)]" }
      foreach ($entry in $map~i~.GetEnumerator()) { Write-Verbose "$($entry.Key) = $($entry.Value)" }
      $total~i~ = ($item~i~ | Measure-Object -Property Length -Sum).Sum -band 0xFF0001
      try { Remove-Item -LiteralPath $item~i~.FullName -WhatIf } catch { Write-Error $_.Exception.Message }
    """.trimIndent().prependIndent("    ") + "\n"
  }
}
