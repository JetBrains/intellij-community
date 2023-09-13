package com.intellij.terminal.completion

data class ShellEnvironment(
  val envs: List<String>,
  val keywords: List<String>,
  val builtins: List<String>,
  val functions: List<String>,
  val commands: List<String>
)