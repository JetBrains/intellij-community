// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.testApps

import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import org.jetbrains.plugins.terminal.util.ShellIntegration
import org.jetbrains.plugins.terminal.util.ShellType

internal object SimpleTextRepeater {

  @JvmStatic
  fun main(arg: Array<String>) {
    check(arg.size % 4 == 0) { "Even argument list size is expected, got " + arg.size }
    val items: List<Item> = (0 until arg.size / 4).map {
      Item.fromCommandline(arg.slice(it * 4 until (it + 1) * 4))
    }
    for (item in items) {
      for (i in 1..item.count) {
        print(item.getTextToPrint(i))
      }
    }
  }

  class Item(private val prefix: String, private val withIncrementingId: Boolean, private val withNewLine: Boolean, val count: Int) {
    internal fun getTextToPrint(id: Int): String {
      return prefix + (if (withIncrementingId) id.toString() else "") + (if (withNewLine) LINE_SEPARATOR else "")
    }

    fun toCommandline(): List<String> {
      return listOf(prefix, withIncrementingId.toString(), withNewLine.toString(), count.toString())
    }

    companion object {
      fun fromCommandline(arg: List<String>): Item {
        return Item(arg[0], arg[1].toBooleanStrict(), arg[2].toBooleanStrict(), arg[3].toInt())
      }
    }
  }

  object Helper {
    fun newLine(shellIntegration: ShellIntegration): Item {
      return when (shellIntegration.shellType) {
        // An empty parameter cannot be escaped uniformly across different PowerShell versions
        ShellType.POWERSHELL -> Item("-", false, true, 1)
        else -> Item("", false, true, 1)
      }
    }

    fun generateCommand(items: List<Item>): List<String> {
      val args: List<String> = items.flatMap { it.toCommandline() }
      return TerminalSessionTestUtil.getJavaCommand(SimpleTextRepeater::class.java, args)
    }

    fun getExpectedOutput(items: List<Item>): String {
      return items.flatMap { item ->
        (1..item.count).map { item.getTextToPrint(it) }
      }.joinToString("")
    }
  }
}

/**
 * Same line separator as used in block command output and in [com.intellij.openapi.editor.Document].
 * @see org.jetbrains.plugins.terminal.exp.ShellCommandOutputScraper
 */
internal const val LINE_SEPARATOR: String = "\n"
