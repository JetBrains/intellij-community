// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal class CommandTreeBuilder(private val command: String,
                                  private val commandSpec: ShellSubcommand,
                                  private val arguments: List<String>) {
  private var curIndex = 0

  fun build(): SubcommandNode {
    val root = SubcommandNode(command, commandSpec, null)
    buildSubcommandTree(root)
    return root
  }

  private fun buildSubcommandTree(root: SubcommandNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val node = root.createChildNode(name) ?: UnknownNode(name, root)
      root.children.add(node)
      curIndex++
      if (node is SubcommandNode) {
        buildSubcommandTree(node)
      }
      else if (node is OptionNode) {
        buildOptionTree(node)
      }
    }
  }

  private fun buildOptionTree(root: OptionNode) {
    while (curIndex < arguments.size) {
      val name = arguments[curIndex]
      val node = root.createChildNode(name)
      if (node != null) {
        root.children.add(node)
        curIndex++
      }
      else return
    }
  }
}
