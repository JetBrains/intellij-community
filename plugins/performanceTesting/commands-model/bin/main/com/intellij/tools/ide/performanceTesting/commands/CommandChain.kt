// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands

/**
 * One or more commands that will be "played" in sequence by IDE
 */
open class CommandChain : MarshallableCommand, Iterable<MarshallableCommand> {
  private val _chain = mutableListOf<MarshallableCommand>()

  override fun iterator(): Iterator<MarshallableCommand> = _chain.iterator()

  override fun storeToString(): String {
    return _chain.joinToString(separator = System.lineSeparator()) { it.storeToString() }
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAMS
   */
  fun addCommand(command: String): CommandChain {
    _chain.add(initMarshallableCommand(command))
    return this
  }

  fun addCommand(command: MarshallableCommand): CommandChain {
    _chain.add(command)
    return this
  }

  fun addCommands(commands: Collection<MarshallableCommand>): CommandChain {
    _chain.addAll(commands)
    return this
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAM_1 .. COMMAND_PARAM_N
   */
  fun addCommand(vararg commandArgs: String): CommandChain {
    val command = initMarshallableCommand(commandArgs.joinToString(separator = " "))
    _chain.add(command)
    return this
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAM_1<separator>COMMAND_PARAM_2<separator>..COMMAND_PARAM_N
   */
  fun addCommandWithSeparator(separator: String, vararg commandArgs: String): CommandChain {
    val command = if (commandArgs.size < 2) {
      initMarshallableCommand(commandArgs[0])
    } else {
      val modifiedCommandArgs = commandArgs[0] + " " + commandArgs[1] +
                                if (commandArgs.size > 2) {
                                  separator + commandArgs.sliceArray(2 until commandArgs.size).joinToString(separator)
                                } else {
                                  ""
                                }
      initMarshallableCommand(modifiedCommandArgs)
    }
    _chain.add(command)
    return this
  }

  fun addCommandChain(commandChain: CommandChain): CommandChain {
    _chain.addAll(commandChain)
    return this
  }

  private fun initMarshallableCommand(content: String): MarshallableCommand =
    object : MarshallableCommand {
      override fun storeToString(): String = content
    }
}