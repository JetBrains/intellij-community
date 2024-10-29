package com.intellij.terminal.completion

import com.intellij.terminal.completion.engine.*
import com.intellij.util.containers.TreeTraversal
import junit.framework.TestCase.assertTrue

internal class ShellCommandTreeAssertionsImpl(root: ShellCommandTreeNode<*>) : ShellCommandTreeAssertions {
  private val allChildren: List<ShellCommandTreeNode<*>> = TreeTraversal.PRE_ORDER_DFS.traversal(root) { node -> node.children }.toList()

  override fun assertSubcommandOf(cmd: String, parentCmd: String) {
    val childNode = allChildren.find { it.text == cmd } ?: error("Not found node with name: $cmd")
    assertTrue("Expected that child is subcommand", childNode is ShellCommandNode)
    assertTrue("Expected that parent of '$cmd' is a subcommand '$parentCmd', but was: ${childNode.parent}",
               (childNode.parent as? ShellCommandNode)?.text == parentCmd)
  }

  override fun assertOptionOf(option: String, subcommand: String) {
    val childNode = allChildren.find { it.text == option } ?: error("Not found node with name: $option")
    assertTrue("Expected that child is option", childNode is ShellOptionNode)
    assertTrue("Expected that parent of '$option' is a subcommand '$subcommand', but was: ${childNode.parent}",
               (childNode.parent as? ShellCommandNode)?.text == subcommand)
  }

  override fun assertArgumentOfOption(arg: String, option: String) {
    val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
    assertTrue("Expected that child is argument", childNode is ShellArgumentNode)
    assertTrue("Expected that parent of '$arg' is an option '$option', but was: ${childNode.parent}",
               (childNode.parent as? ShellOptionNode)?.text == option)
  }

  override fun assertArgumentOfSubcommand(arg: String, subcommand: String) {
    val childNode = allChildren.find { it.text == arg } ?: error("Not found node with name: $arg")
    assertTrue("Expected that child is argument", childNode is ShellArgumentNode)
    assertTrue("Expected that parent of '$arg' is an option '$subcommand', but was: ${childNode.parent}",
               (childNode.parent as? ShellCommandNode)?.text == subcommand)
  }

  override fun assertUnknown(child: String, parent: String) {
    val childNode = allChildren.find { it.text == child } ?: error("Not found node with name: $child")
    assertTrue("Expected that child is unknown", childNode is ShellUnknownNode)
    assertTrue("Expected that parent of '$child' is '$parent', but was: ${childNode.parent}",
               childNode.parent?.text == parent)
  }
}
