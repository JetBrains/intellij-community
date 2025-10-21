// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Entry point for providing shell command specifications, used for command completion in the New Terminal.
 * Implement this interface and add it as an extension to the plugin.xml:
 * ```
 * <extensions defaultExtensionNs="org.jetbrains.plugins.terminal">
 *    <commandSpecsProvider implementation="your implementation FQN"/>
 * </extensions>
 * ```
 */
@ApiStatus.Experimental
interface ShellCommandSpecsProvider {
  /**
   * Provide the list of command specs.
   *
   * Use [ShellCommandSpecInfo.create][ShellCommandSpecInfo.Companion.create] to create the [ShellCommandSpecInfo].
   *
   * To create [ShellCommandSpec][com.intellij.terminal.completion.spec.ShellCommandSpec] please use the [helper function][ShellCommandSpec].
   * **Please do not override the original interface**.
   *
   * Use [ShellCommandSpecConflictStrategy.REPLACE] strategy if there is already some spec for your command,
   * and you want your spec to replace the original one.
   */
  fun getCommandSpecs(): List<ShellCommandSpecInfo>

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<ShellCommandSpecsProvider> = ExtensionPointName.create("org.jetbrains.plugins.terminal.commandSpecsProvider")
  }
}