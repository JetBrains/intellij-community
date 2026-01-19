// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.createCacheKey
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators.emptyListGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import java.util.function.Supplier
import javax.swing.Icon

internal class ShellCommandSpecImpl(
  override val name: String,
  override val displayName: String? = null,
  private val descriptionSupplier: Supplier<@Nls String>? = null,
  override val insertValue: String? = null,
  override val priority: Int = 50,
  override val requiresSubcommand: Boolean = false,
  override val parserOptions: ShellCommandParserOptions = ShellCommandParserOptions.DEFAULT,
  override val subcommandsGenerator: ShellRuntimeDataGenerator<List<ShellCommandSpec>> = emptyListGenerator(),
  private val dynamicOptionsSupplier: (suspend (ShellRuntimeContext) -> List<ShellOptionSpec>)? = null,
  private val staticOptionsSupplier: () -> List<ShellOptionSpec> = { emptyList() },
  private val argumentsSupplier: () -> List<ShellArgumentSpec> = { emptyList() },
  private val parentNames: List<String> = emptyList()
) : ShellCommandSpec {
  override val options: List<ShellOptionSpec> by lazy { staticOptionsSupplier() }
  override val arguments: List<ShellArgumentSpec> by lazy { argumentsSupplier() }

  override val description: String?
    get() = descriptionSupplier?.get()

  // the icon of command will be specified in the completion logic
  override val icon: Icon? = null

  override val prefixReplacementIndex: Int = 0

  override val isHidden: Boolean = false

  override val shouldEscape: Boolean = true

  init {
    if (priority !in 0..100) {
      error("Priority must be between 0 and 100")
    }
  }

  override val allOptionsGenerator: ShellRuntimeDataGenerator<List<ShellOptionSpec>> = createOptionsGenerator()

  // Return non cacheable generator if there are no dynamic options. Static options are already lazy.
  private fun createOptionsGenerator(): ShellRuntimeDataGenerator<List<ShellOptionSpec>> {
    val cacheKey = createCacheKey(parentNames + name, "options")
    return if (dynamicOptionsSupplier == null) {
      ShellRuntimeDataGenerator(debugName = cacheKey) { options }
    }
    else {
      ShellRuntimeDataGenerator(cacheKeyAndDebugName = cacheKey) { context ->
        val dynamicOptions = dynamicOptionsSupplier.invoke(context)
        (dynamicOptions + options).distinctBy { it.name }
      }
    }
  }

  override fun toString(): String {
    return "ShellCommandSpecImpl(name=$name, displayName=$displayName, insertValue=$insertValue, priority=$priority, requiresSubcommand=$requiresSubcommand, parserOptions=$parserOptions, description=$description, subcommandsGenerator=$subcommandsGenerator, optionsGenerator=$allOptionsGenerator, options=$options, arguments=$arguments)"
  }
}
