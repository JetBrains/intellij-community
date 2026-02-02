// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import java.util.concurrent.atomic.AtomicReference

const val DELEGATE_RUNNER_ID: String = "MAVEN_DELEGATE_BUILD_RUNNER"
private val LOG = logger<DelegateBuildRunner>()

internal class DelegateBuildRunner : DefaultJavaProgramRunner() {
  override fun getRunnerId() = DELEGATE_RUNNER_ID

  @Throws(ExecutionException::class)
  override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
    val executionResult = state.execute(environment.executor, this) ?: return null
    val result = AtomicReference<RunContentDescriptor?>()
    ApplicationManager.getApplication().invokeAndWait {
      val runContentBuilder = RunContentBuilder(executionResult, environment)

      val runContentDescriptor = runContentBuilder.showRunContent(environment.contentToReuse) ?: return@invokeAndWait

      val descriptor = object : RunContentDescriptor(runContentDescriptor.executionConsole, runContentDescriptor.processHandler,
                                                     runContentDescriptor.component, runContentDescriptor.displayName,
                                                     runContentDescriptor.icon, null,
                                                     runContentDescriptor.restartActions) {
        override fun isHiddenContent() = true
      }
      descriptor.runnerLayoutUi = runContentDescriptor.runnerLayoutUi
      result.set(descriptor)
    }
    return result.get()
  }

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is MavenRunConfiguration
  }

  override fun doExecuteAsync(state: TargetEnvironmentAwareRunProfileState, env: ExecutionEnvironment): Promise<RunContentDescriptor?> {
    return state.prepareTargetToCommandExecution(env, LOG, "Failed to execute delegate run configuration async") { doExecute(state, env) }
  }

  object Util {
    @JvmStatic
    fun getDelegateRunner(): ProgramRunner<*>? = ProgramRunner.findRunnerById(DELEGATE_RUNNER_ID)
  }

}
