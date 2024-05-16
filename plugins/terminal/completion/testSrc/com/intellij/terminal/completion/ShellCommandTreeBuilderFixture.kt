package com.intellij.terminal.completion

import com.intellij.terminal.completion.engine.ShellCommandNode
import com.intellij.terminal.completion.engine.ShellCommandTreeBuilder
import com.intellij.terminal.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShellCommandTreeBuilderFixture(
  private val commandSpecManager: ShellCommandSpecsManager,
  private val generatorsExecutor: ShellDataGeneratorsExecutor,
  private val contextProvider: ShellRuntimeContextProvider
) {
  suspend fun buildCommandTreeAndTest(spec: ShellCommandSpec, arguments: List<String>, assert: (ShellCommandTreeAssertions) -> Unit) {
    val rootNode: ShellCommandNode = ShellCommandTreeBuilder.build(contextProvider, generatorsExecutor, commandSpecManager,
                                                                   spec.name, spec, arguments)
    val assertions = ShellCommandTreeAssertionsImpl(rootNode)
    assert(assertions)
  }
}