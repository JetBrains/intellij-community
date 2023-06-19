// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

internal abstract class CommandPartNode<T>(val text: String, open val spec: T?, val parent: CommandPartNode<*>?) {
  val children: MutableList<CommandPartNode<*>> = mutableListOf()

  abstract fun createChildNode(name: String): CommandPartNode<*>?

  override fun toString(): String {
    return "${javaClass.simpleName} { text: $text, children: $children }"
  }
}

internal class SubcommandNode(text: String,
                              override val spec: ShellSubcommand,
                              parent: CommandPartNode<*>?) : CommandPartNode<ShellSubcommand>(text, spec, parent) {
  override fun createChildNode(name: String): CommandPartNode<*>? {
    val subcommand = spec.subcommands.find { it.names.contains(name) }
    if (subcommand != null && children.isEmpty()) {
      // subcommand can be only on the first place
      return SubcommandNode(name, subcommand, this)
    }
    getAllAvailableOptions().find { it.names.contains(name) }?.let {
      return OptionNode(name, it, this)
    }
    return if (spec.args.size > children.count { it is ArgumentNode }) {
      ArgumentNode(name, this)
    }
    else null
  }

  fun getAllAvailableOptions(): List<ShellOption> {
    val options = mutableListOf<ShellOption>()
    options.addAll(spec.options)
    var cur = parent
    while (cur is SubcommandNode) {
      // parent commands can define 'persistent' options - they can be used in all nested subcommands
      options.addAll(cur.spec.options.filter { it.isPersistent })
      cur = cur.parent
    }
    return options
  }
}

internal class OptionNode(text: String,
                          override val spec: ShellOption,
                          parent: CommandPartNode<*>?) : CommandPartNode<ShellOption>(text, spec, parent) {
  override fun createChildNode(name: String): CommandPartNode<*>? {
    return if (spec.args.size > children.count { it is ArgumentNode }) {
      ArgumentNode(name, this)
    }
    else null
  }
}

internal class ArgumentNode(text: String, parent: CommandPartNode<*>?) : CommandPartNode<ShellArgument>(text, null, parent) {
  override fun createChildNode(name: String): CommandPartNode<*>? {
    return null
  }
}

internal class UnknownNode(text: String, parent: CommandPartNode<*>?) : CommandPartNode<Any>(text, null, parent) {
  override fun createChildNode(name: String): CommandPartNode<*>? {
    return null
  }
}