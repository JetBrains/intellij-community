// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Different strategies of resolving conflicts when there are a couple of specs for the single shell command.
 * Such a conflict is a usual case - some command spec may be defined in the Terminal plugin itself,
 * while other plugins may want to extend it.
 */
@ApiStatus.Experimental
enum class ShellCommandSpecConflictStrategy {
  /**
   * Replaces the spec defined as [DEFAULT].
   * But the effective spec will still be calculated by merging this one with the other specs defined with [OVERRIDE] strategy.
   * If there are two or more such specs, the result is undefined.
   *
   * Use this strategy if you want to completely replace the base spec defined in the Terminal plugin.
   */
  REPLACE,

  /**
   * Allows overriding base specs with the strategy [DEFAULT] or [REPLACE].
   * The effective spec will consist of all subcommands, options and arguments from the base spec,
   * plus all the parts from the overriding spec.
   *
   * If some command or option is defined in both specs, then it will be taken from the overriding spec.
   * This merge is recursive for subcommands, so it is not required to redefine the whole subcommand
   * if you only need to add some option to it.
   *
   * But it is not recursive for options and arguments.
   * If an option is defined in the overriding spec, only its content will be effective.
   * Arguments always must be defined in the subcommands/options of the overriding spec if there should be any,
   * because they are not taken from the base spec.
   *
   * If there are two or more specs with this strategy, and they override the same subcommand/option, the result is undefined.
   *
   * See the implementation of merged spec for more details:
   * [ShellMergedCommandSpec][org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellMergedCommandSpec]
   */
  OVERRIDE,

  /**
   * Strategy for base specs. Specs with this strategy can be overridden or replaced with other specs.
   *
   * Use this strategy if there is no spec for your command in the Terminal plugin and bundled IDE plugins.
   */
  DEFAULT
}