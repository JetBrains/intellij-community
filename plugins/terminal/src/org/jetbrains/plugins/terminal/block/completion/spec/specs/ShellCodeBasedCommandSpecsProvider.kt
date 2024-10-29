// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.specs

import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecConflictStrategy.*
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecInfo
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpecsProvider
import org.jetbrains.plugins.terminal.block.completion.spec.specs.make.ShellMakeCommandSpec

internal class ShellCodeBasedCommandSpecsProvider : ShellCommandSpecsProvider {
  override fun getCommandSpecs(): List<ShellCommandSpecInfo> = listOf(
    ShellCommandSpecInfo.create(cdCommandSpec(), REPLACE),
    ShellCommandSpecInfo.create(ShellMakeCommandSpec.create(), OVERRIDE),
  )
}
