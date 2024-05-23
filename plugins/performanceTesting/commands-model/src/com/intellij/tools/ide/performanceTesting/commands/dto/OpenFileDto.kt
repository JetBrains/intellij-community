package com.intellij.tools.ide.performanceTesting.commands.dto

data class OpenFileDto(
  val file: String,
  val timeout: Long = 0L,
  val suppressErrors: Boolean = false,
  val disableCodeAnalysis: Boolean = false
)