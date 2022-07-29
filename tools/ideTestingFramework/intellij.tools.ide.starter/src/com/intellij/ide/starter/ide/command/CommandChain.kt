package com.intellij.ide.starter.ide.command

/**
 * One or more commands, that will be "played" in sequence by IDE
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
  fun addCommand(command: String) {
    _chain.add(initMarshallableCommand(command))
  }

  fun addCommand(command: MarshallableCommand) {
    _chain.add(command)
  }

  /**
   * Pattern for adding a command: %YOUR_COMMAND_PREFIX COMMAND_PARAM_1 .. COMMAND_PARAM_N
   */
  fun addCommand(vararg commandArgs: String) {
    val command = initMarshallableCommand(commandArgs.joinToString(separator = " "))
    _chain.add(command)
  }

  fun addCommandChain(commandChain: CommandChain) {
    _chain.addAll(commandChain)
  }

  private fun initMarshallableCommand(content: String): MarshallableCommand =
    object : MarshallableCommand {
      override fun storeToString(): String = content
    }
}