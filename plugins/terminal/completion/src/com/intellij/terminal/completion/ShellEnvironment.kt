package com.intellij.terminal.completion

data class ShellEnvironment(
  val envs: List<String> = emptyList(),
  val keywords: List<String> = emptyList(),
  val builtins: List<String> = emptyList(),
  val functions: List<String> = emptyList(),
  val commands: List<String> = emptyList(),
  val aliases: Map<String, String> = emptyMap()
)