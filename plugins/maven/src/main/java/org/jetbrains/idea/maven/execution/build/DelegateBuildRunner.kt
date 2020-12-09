// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.build

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.concurrency.Promise

internal class DelegateBuildRunner : DefaultJavaProgramRunner() {
  override fun getRunnerId() = ID

  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val executionResult = state.execute(environment.executor, this) ?: return null
    val runContentBuilder = RunContentBuilder(executionResult, environment)

    val runContentDescriptor = runContentBuilder.showRunContent(environment.contentToReuse) ?: return null

    val descriptor = object : RunContentDescriptor(runContentDescriptor.executionConsole, runContentDescriptor.processHandler,
                                                   runContentDescriptor.component, runContentDescriptor.displayName,
                                                   runContentDescriptor.icon, null,
                                                   runContentDescriptor.restartActions) {
      override fun isHiddenContent() = true
    }
    descriptor.runnerLayoutUi = runContentDescriptor.runnerLayoutUi
    return descriptor
  }

  override fun doExecuteAsync(state: TargetEnvironmentAwareRunProfileState, env: ExecutionEnvironment): Promise<RunContentDescriptor?> {
    return state.prepareTargetToCommandExecution(env, LOG, "Failed to execute delegate run configuration async") { doExecute(state, env) }
  }

  companion object {
    private const val ID = "MAVEN_DELEGATE_BUILD_RUNNER"
    private val LOG = logger<DelegateBuildRunner>()

    @JvmStatic
    fun getDelegateRunner(): ProgramRunner<*>? = ProgramRunner.findRunnerById(ID)
  }
}
