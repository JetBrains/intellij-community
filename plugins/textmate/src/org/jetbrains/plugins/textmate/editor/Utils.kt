package org.jetbrains.plugins.textmate.editor

import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class Utils {
  companion object {
    private val WHITESPACES = arrayOf(' ', '\t')

    fun String.indentOfLine(options: CommonCodeStyleSettings.IndentOptions): Int =
      this.toCharArray()
        .takeWhile { it in WHITESPACES }
        .sumOf { if (it == '\t') options.TAB_SIZE else 1 }
  }
}