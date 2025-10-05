// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block.util

import org.jetbrains.plugins.terminal.block.completion.spec.json.ShellJsonCommandSpecsProvider

internal class TestJsonCommandSpecsProvider : ShellJsonCommandSpecsProvider() {
  override val shortDescriptionsJsonPath: String = "commandSpecs/all_commands.json"
  override val commandSpecsPath: String = "commandSpecs"
}
