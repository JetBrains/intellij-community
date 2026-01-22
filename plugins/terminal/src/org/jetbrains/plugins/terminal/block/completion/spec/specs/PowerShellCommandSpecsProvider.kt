// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs

import com.intellij.terminal.completion.spec.ShellCommandParserOptions
import org.jetbrains.plugins.terminal.TerminalBundle
import org.jetbrains.plugins.terminal.block.completion.spec.*

internal class PowerShellCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> {
    return listOf(
      pathSuggestingCommandSpec("Get-Content"),
      pathSuggestingCommandSpec("Add-Content"),
      pathSuggestingCommandSpec("Set-Content"),
      pathSuggestingCommandSpec("Clear-Content"),
      pathSuggestingCommandSpec("Set-Location", onlyDirectories = true),
      pathSuggestingCommandSpec("Get-ChildItem"),
      pathSuggestingCommandSpec("Remove-Item"),
      pathSuggestingCommandSpec("Rename-Item"),
      pathAndDestinationSuggestingCommandSpec("Copy-Item"),
      pathAndDestinationSuggestingCommandSpec("Move-Item"),
    )
  }
}

private fun pathSuggestingCommandSpec(commandName: String, onlyDirectories: Boolean = false): ShellCommandSpecInfo {
  val spec = ShellCommandSpec(commandName) {
    parserOptions(
      ShellCommandParserOptions.builder()
        .flagsArePosixNonCompliant(true)
        .build()
    )

    option("-Path") {
      argument {
        displayName(TerminalBundle.message("powershell.specs.argument.path"))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator(onlyDirectories))
      }
    }

    argument {
      displayName(TerminalBundle.message("powershell.specs.argument.path"))
      optional()
      suggestions(ShellDataGenerators.fileSuggestionsGenerator(onlyDirectories))
    }
  }

  return ShellCommandSpecInfo.create(spec, ShellCommandSpecConflictStrategy.DEFAULT)
}

private fun pathAndDestinationSuggestingCommandSpec(commandName: String): ShellCommandSpecInfo {
  val spec = ShellCommandSpec(commandName) {
    parserOptions(
      ShellCommandParserOptions.builder()
        .flagsArePosixNonCompliant(true)
        .build()
    )

    option("-Path") {
      argument {
        displayName(TerminalBundle.message("powershell.specs.argument.path"))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator())
      }
    }
    option("-Destination") {
      argument {
        displayName(TerminalBundle.message("powershell.specs.argument.path"))
        suggestions(ShellDataGenerators.fileSuggestionsGenerator())
      }
    }

    argument {
      displayName(TerminalBundle.message("powershell.specs.argument.srcPath"))
      optional()
      suggestions(ShellDataGenerators.fileSuggestionsGenerator())
    }
    argument {
      displayName(TerminalBundle.message("powershell.specs.argument.dstPath"))
      optional()
      suggestions(ShellDataGenerators.fileSuggestionsGenerator())
    }
  }

  return ShellCommandSpecInfo.create(spec, ShellCommandSpecConflictStrategy.DEFAULT)
}