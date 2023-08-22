package com.intellij.terminal.completion

import org.jetbrains.terminal.completion.ShellArgument

object CommandSpecCompletionUtil {
  fun ShellArgument.isFilePath(): Boolean = isWithTemplate("filepaths")

  fun ShellArgument.isFolder(): Boolean = isWithTemplate("folders")

  private fun ShellArgument.isWithTemplate(template: String): Boolean {
    return templates.contains(template) || generators.any { it.templates.contains(template) }
  }
}