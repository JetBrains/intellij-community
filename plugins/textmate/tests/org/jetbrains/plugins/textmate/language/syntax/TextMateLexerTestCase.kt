package org.jetbrains.plugins.textmate.language.syntax

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.textmate.joni.JoniRegexFactory
import junit.framework.TestCase.assertNotNull
import org.jetbrains.plugins.textmate.TestUtil.findScopeByFileName
import org.jetbrains.plugins.textmate.TestUtil.loadBundle
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateHighlightingLexer
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateSyntaxMatcherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory
import java.io.File
import java.nio.charset.StandardCharsets

abstract class TextMateLexerTestCase {
  private val TEST_DATA_BASE_DIR: String = "${PlatformTestUtil.getCommunityPath()}/plugins/textmate/testData/lexer"

  fun doTest(beforePath: String, afterPath: String) {
    val beforeFile = File(TEST_DATA_BASE_DIR, "$testDirRelativePath/$beforePath")
    val afterFile = File(TEST_DATA_BASE_DIR, "$testDirRelativePath/$afterPath")

    val syntaxTableBuilder = TextMateSyntaxTableBuilder(TextMateConcurrentMapInterner())
    val matchers = syntaxTableBuilder.loadBundle(bundleName)
    for (bundleName in extraBundleNames) {
      syntaxTableBuilder.loadBundle(bundleName)
    }

    val syntaxTable = syntaxTableBuilder.build()
    val rootScope = findScopeByFileName(matchers, beforeFile.name)
    assertNotNull("scope is empty for file name: " + beforeFile.name, rootScope)

    val sourceData = StringUtil.convertLineSeparators(FileUtil.loadFile(beforeFile, StandardCharsets.UTF_8))

    val text = sourceData.replace("$(\\n+)".toRegex(), "")
    val regexProvider = CaffeineCachingRegexProvider(RememberingLastMatchRegexFactory(JoniRegexFactory()))

    TextMateSelectorWeigherImpl().caching().use { weigher ->
      val syntaxMatcher = TextMateSyntaxMatcherImpl(regexProvider, weigher)
      val lexer: Lexer = TextMateHighlightingLexer(syntaxTable.getLanguageDescriptor(rootScope), syntaxMatcher, -1)
      val output = buildString {
        lexer.start(text)
        while (lexer.getTokenType() != null) {
          val startIndex = lexer.getTokenStart()
          val endIndex = lexer.getTokenEnd()
          val tokenType: IElementType = lexer.getTokenType()!!
          val str = "${getTokenTypePresentation(tokenType)}: [$startIndex, $endIndex], {${text.substring(startIndex, endIndex)}}\n"
          append(str)
          lexer.advance()
        }
      }.trim { it <= ' ' }
      UsefulTestCase.assertSameLinesWithFile(afterFile.path, output)
    }
  }

  private fun getTokenTypePresentation(tokenType: IElementType): String? {
    return if (tokenType is TextMateElementType) {
      val scope = tokenType.scope
      buildString {
        val scopeName: CharSequence? = scope.scopeName
        if (scopeName != null) {
          append(scopeName)
        }

        var parent: TextMateScope? = scope.parent
        while (parent != null) {
          val parentScopeName: CharSequence? = parent.scopeName
          if (parentScopeName != null) {
            if (!isEmpty()) {
              insert(0, ";")
            }
            insert(0, parentScopeName)
          }
          parent = parent.parent
        }
      }.trim { it <= ' ' }
    }
    else {
      tokenType.toString()
    }
  }

  abstract val testDirRelativePath: String

  abstract val bundleName: String

  open val extraBundleNames: List<String> = emptyList()
}