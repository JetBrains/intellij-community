package com.intellij.ide.starter.ide.command

open class CommandChain : MarshallableCommand, Iterable<MarshallableCommand> {
  private val _chain = mutableListOf<MarshallableCommand>()

  override fun iterator(): Iterator<MarshallableCommand> = _chain.iterator()

  override fun storeToString(): String {
    return _chain.joinToString(separator = System.lineSeparator()) { it.storeToString() }
  }

  fun addCommand(command: String) {
    _chain.add(initMarshallableCommand(command))
  }

  fun addCommand(vararg commandArgs: String) {
    val command = initMarshallableCommand(commandArgs.joinToString(separator = " "))
    _chain.add(command)
  }

  private fun initMarshallableCommand(content: String): MarshallableCommand =
    object : MarshallableCommand {
      override fun storeToString(): String = content
    }
}