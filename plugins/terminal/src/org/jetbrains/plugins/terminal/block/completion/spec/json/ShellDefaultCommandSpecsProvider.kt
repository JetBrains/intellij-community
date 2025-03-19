// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

internal class ShellDefaultCommandSpecsProvider : ShellJsonCommandSpecsProvider() {
  override val shortDescriptionsJsonPath: String = "completionSpecs/all_commands.json"
  override val commandSpecsPath: String = "completionSpecs"
}