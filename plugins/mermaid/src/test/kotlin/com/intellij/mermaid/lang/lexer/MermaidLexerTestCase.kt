package com.intellij.mermaid.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LexerTestCase
import java.util.*

abstract class MermaidLexerTestCase : LexerTestCase() {
  abstract val diagramName: String

  override fun createLexer(): Lexer {
    return MermaidLexer()
  }

  override fun getDirPath(): String {
    return "src/test/resources/lexer"
  }

  override fun getPathToTestDataFile(extension: String?): String {
    return dirPath + "/" + diagramName + "/" + getTestName(true) + extension
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return getTestName(getCamelCaseTestName(), lowercaseFirstLetter)
  }

  private fun getCamelCaseTestName(): String {
    return StringUtil.trimStart(super.getName(), "test").split(" ").toMutableList().joinToString("") { word ->
      word.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
          Locale.getDefault()
        ) else it.toString()
      }
    }
  }
}
