package com.intellij.terminal.completion

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.terminal.completion.ShellArgument

@ApiStatus.Internal
object CommandSpecCompletionUtil {
  fun ShellArgument.isFilePath(): Boolean = isWithTemplate("filepaths")

  fun ShellArgument.isFolder(): Boolean = isWithTemplate("folders")

  private fun ShellArgument.isWithTemplate(template: String): Boolean {
    return templates.contains(template) || generators.any { it.templates.contains(template) }
  }
}