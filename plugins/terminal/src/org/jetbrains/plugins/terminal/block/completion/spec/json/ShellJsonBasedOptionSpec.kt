// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.json

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellOptionSpec
import org.jetbrains.terminal.completion.ShellOption
import javax.swing.Icon

/**
 * @param [parentCommandNames] used to build cache key/debug name of the argument's generators
 */
internal class ShellJsonBasedOptionSpec(
  override val name: String,
  private val data: ShellOption,
  private val parentCommandNames: List<String>
) : ShellOptionSpec {
  override val displayName: String?
    get() = data.displayName

  override val description: String?
    get() = data.description

  override val insertValue: String?
    get() = data.insertValue

  override val priority: Int
    get() = data.priority

  // the icon of option will be specified in the completion logic
  override val icon: Icon? = null

  override val prefixReplacementIndex: Int = 0

  override val isHidden: Boolean = false

  override val shouldEscape: Boolean = true

  override val isRequired: Boolean
    get() = data.isRequired

  override val isPersistent: Boolean
    get() = data.isPersistent

  override val separator: String?
    get() = data.separator

  override val repeatTimes: Int
    get() = data.repeatTimes

  override val exclusiveOn: List<String>
    get() = data.exclusiveOn

  override val dependsOn: List<String>
    get() = data.dependsOn

  override val arguments: List<ShellArgumentSpec> by lazy {
    data.args.map { ShellJsonBasedArgumentSpec(it, parentCommandNames) }
  }

  override fun toString(): String {
    return "ShellJsonBasedOptionSpec(name=$name, parentCommandNames=$parentCommandNames, data=$data)"
  }
}
