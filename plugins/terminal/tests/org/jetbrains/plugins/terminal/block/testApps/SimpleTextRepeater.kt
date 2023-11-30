// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.testApps

import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil

object SimpleTextRepeater {

  @JvmStatic
  fun main(arg: Array<String>) {
    val chunks: MutableList<Item> = mutableListOf()
    check(arg.size % 2 == 0) { "Even argument list size is expected, got " + arg.size }
    for (i in 0..<arg.size / 2) {
      chunks.add(Item(arg[i * 2], arg[i * 2 + 1].toInt()))
    }
    for (chunk in chunks) {
      repeat(chunk.count) {
        println(chunk.lineText)
      }
    }
  }

  data class Item(val lineText: String, val count: Int)

  object Helper {
    fun generateCommandLine(items: List<Item>): String {
      val args: Array<String> = items.flatMap { listOf(it.lineText, it.count.toString()) }.toTypedArray()
      return TerminalSessionTestUtil.getJavaShellCommand(SimpleTextRepeater::class.java, *args)
    }

    fun getExpectedOutput(items: List<Item>): String {
      val expectedLines = items.flatMap { item ->
        MutableList(item.count) { item.lineText }
      }
      return expectedLines.joinToString("\n", postfix = "\n")
    }
  }
}