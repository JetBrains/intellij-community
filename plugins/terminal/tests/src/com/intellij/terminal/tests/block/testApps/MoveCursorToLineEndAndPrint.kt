// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.testApps

import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import com.jediterm.core.util.TermSize

internal object MoveCursorToLineEndAndPrint {

  @JvmStatic
  fun main(arg: Array<String>) {
    check(arg.isNotEmpty()) { "One or more arguments are expected, got " + arg.size }
    for (textToPrint in arg) {
      check(textToPrint.isNotEmpty())
      // [java.io.Console] doesn't provide terminal width =>
      // To place the cursor at the line end, move the cursor far away to the right - it should stop as it reaches the edge.
      val infiniteWidth = 10000
      print("\u001b[${infiniteWidth}C")
      print(textToPrint)
    }
    println() // new line to get rid of trailing '%' in zsh
  }

  object Helper {
    fun generateCommand(textsToPrint: List<String>): List<String> {
      return TerminalSessionTestUtil.getJavaCommand(MoveCursorToLineEndAndPrint::class.java, textsToPrint)
    }

    fun getExpectedOutput(termSize: TermSize, textsToPrint: List<String>): String {
      return " ".repeat(termSize.columns - 1) + textsToPrint.dropLast(1).joinToString("") {
        val emptyColumns = termSize.columns - (it.length - 1) % termSize.columns
        check(emptyColumns > 0)
        if (emptyColumns == termSize.columns) {
          it.dropLast(1) // string ends at the right edge
        }
        else {
          it + " ".repeat(emptyColumns - 1)
        }
      } + textsToPrint.last() + LINE_SEPARATOR
    }
  }
}
