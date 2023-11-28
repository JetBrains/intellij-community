package com.intellij.mermaid.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.mermaid.lang.MermaidTestingUtil
import com.intellij.testFramework.LexerTestCase

abstract class MermaidLexerTestCase : LexerTestCase() {
  abstract val diagramName: String

  override fun createLexer(): Lexer {
    return MermaidLexer()
  }

  override fun getDirPath(): String {
    return "${MermaidTestingUtil.TEST_DATA_PATH}/lexer"
  }

  override fun getPathToTestDataFile(extension: String): String {
    return dirPath + "/" + diagramName + "/" + getTestName(true) + extension
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    return MermaidTestingUtil.getTestName(name, lowercaseFirstLetter)
  }
}
