package com.intellij.terminal.completion

import com.intellij.terminal.completion.engine.ShellArgumentNode
import com.intellij.terminal.completion.engine.ShellCommandNode
import com.intellij.terminal.completion.engine.ShellCommandTreeNode
import com.intellij.terminal.completion.engine.ShellOptionNode
import com.intellij.terminal.completion.engine.ShellUnknownNode
import com.intellij.util.containers.TreeTraversal
import org.junit.jupiter.api.Assertions.assertTrue

internal class ShellCommandTreeAssertionsImpl(root: ShellCommandTreeNode<*>) : ShellCommandTreeAssertions {
  private val allChildren: List<ShellCommandTreeNode<*>> = TreeTraversal.PRE_ORDER_DFS.traversal(root) { node -> node.children }.toList()

  override fun assertSubcommandOf(cmd: String, parentCmd: String) {
    val childNode = allChildren.find { it.text == cmd } ?: error("Not found node with name: $cmd")
    assertTrue(childNode is ShellCommandNode, "Expected that child is subcommand")
    assertTrue((childNode.parent as? ShellCommandNode)?.text == parentCmd,
               "Expected that parent of '$cmd' is a subcommand '$parentCmd', but was: ${childNode.parent}")
  }

  override fun assertOptionOf(option: String, subcommand: String) {
    val childNode = allChildren.find { it.text == option } ?: error("Not found node with name: $option")
    assertTrue(childNode is ShellOptionNode, "Expected that child is option")
    assertTrue((childNode.parent as? ShellCommandNode)?.text == subcommand,
               "Expected that parent of '$option' is a subcommand '$subcommand', but was: ${childNode.parent}")
  }

  override fun assertArgumentOfOption(arg: String, option: String) {
    val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
    assertTrue(childNode is ShellArgumentNode, "Expected that child is argument")
    assertTrue((childNode.parent as? ShellOptionNode)?.text == option,
               "Expected that parent of '$arg' is an option '$option', but was: ${childNode.parent}")
  }

  override fun assertArgumentOfSubcommand(arg: String, subcommand: String) {
    val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
    assertTrue(childNode is ShellArgumentNode, "Expected that child is argument")
    assertTrue((childNode.parent as? ShellCommandNode)?.text == subcommand,
               "Expected that parent of '$arg' is an option '$subcommand', but was: ${childNode.parent}")
  }

  override fun assertUnknown(child: String, parent: String) {
    val childNode = allChildren.find { it.text == child } ?: error("Not found node with name: $child")
    assertTrue(childNode is ShellUnknownNode, "Expected that child is unknown")
    assertTrue(childNode.parent?.text == parent,
               "Expected that parent of '$child' is '$parent', but was: ${childNode.parent}")
  }
}
