// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.HelperPackage
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.CommandLinePatcher
import com.jetbrains.python.run.PythonScriptExecution
import com.jetbrains.python.run.PythonScriptTargetedCommandLineBuilder
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import java.util.function.Function

class PyRerunFailedTestsAction(componentContainer: ComponentContainer) : AbstractRerunFailedTestsAction(componentContainer) {
  override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
    val model = model ?: return null
    return MyTestRunProfile(model.properties.configuration as AbstractPythonRunConfiguration<*>)
  }

  private inner class MyTestRunProfile(configuration: RunConfigurationBase<*>) : MyRunProfile(configuration) {
    override fun getModules(): Array<Module> = (peer as AbstractPythonRunConfiguration<*>).modules

    @Throws(ExecutionException::class)
    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
      val configuration = peer as AbstractPythonTestRunConfiguration<*>

      // If configuration wants to take care about rerun itself
      if (configuration is TestRunConfigurationReRunResponsible) {
        // TODO: Extract method
        val failedTestElements = mutableSetOf<PsiElement>()
        for (proxy in getFailedTests(project)) {
          val location = proxy.getLocation(project, GlobalSearchScope.allScope(project))
          if (location != null) {
            failedTestElements.add(location.psiElement)
          }
        }
        return (configuration as TestRunConfigurationReRunResponsible).rerunTests(executor, env, failedTestElements)
      }
      val state = configuration.getState(executor, env) ?: return null
      return FailedPythonTestCommandLineStateBase(configuration, env, state as PythonTestCommandLineStateBase<*>)
    }
  }

  private inner class FailedPythonTestCommandLineStateBase(configuration: AbstractPythonTestRunConfiguration<*>,
                                                           env: ExecutionEnvironment?,
                                                           private val state: PythonTestCommandLineStateBase<*>)
    : PythonTestCommandLineStateBase<AbstractPythonTestRunConfiguration<*>>(configuration, env) {
    private val project: Project

    init {
      project = configuration.project
    }

    override fun getRunner(): HelperPackage = state.runner

    override fun getTestLocator(): SMTestLocator? = state.testLocator

    @Throws(ExecutionException::class)
    override fun execute(executor: Executor, processStarter: PythonProcessStarter, vararg patchers: CommandLinePatcher): ExecutionResult {
      // Insane rerun tests with out of spec.
      if (testSpecs.isEmpty()) {
        throw ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"))
      }
      return super.execute(executor, processStarter, *patchers)
    }

    @Throws(ExecutionException::class)
    override fun execute(executor: Executor, converter: PythonScriptTargetedCommandLineBuilder): ExecutionResult {
      // Insane rerun tests with out of spec.
      if (testSpecs.isEmpty()) {
        throw ExecutionException(PyBundle.message("runcfg.tests.cant_rerun"))
      }
      return super.execute(executor, converter)
    }

    /**
     * *To be deprecated. The part of the legacy implementation based on [GeneralCommandLine].*
     */
    override fun getTestSpecs(): List<String> {
      // Method could be called on any thread (as any method of this class), and we need read action
      return ReadAction.compute(ThrowableComputable<List<String>, RuntimeException> { getTestSpecImpl() })
    }

    override fun getTestSpecs(request: TargetEnvironmentRequest): List<TargetEnvironmentFunction<String>> =
      ReadAction.compute(ThrowableComputable<List<TargetEnvironmentFunction<String>>, RuntimeException> {
        getTestSpecImpl(request)
      })

    @RequiresReadLock
    private fun getTestSpecImpl(): List<String> {
      val failedTests = getFailedTests(project)
      val failedTestLocations = getTestLocations(failedTests)
      val result: List<String> =
        if (configuration is PyRerunAwareConfiguration) {
          (configuration as PyRerunAwareConfiguration).getTestSpecsForRerun(myConsoleProperties.scope, failedTestLocations)
        }
        else {
          failedTestLocations.mapNotNull { configuration.getTestSpec(it.first, it.second) }
        }
      if (result.isEmpty()) {
        val locations = failedTests.map { it.locationUrl }
        LOG.warn("Can't resolve specs for the following tests: ${locations.joinToString(separator = ", ")}")
      }
      return result
    }

    @RequiresReadLock
    private fun getTestSpecImpl(request: TargetEnvironmentRequest): List<TargetEnvironmentFunction<String>> {
      val failedTests = getFailedTests(project)
      val failedTestLocations = getTestLocations(failedTests)
      val result: List<TargetEnvironmentFunction<String>> =
        if (configuration is PyRerunAwareConfiguration) {
          (configuration as PyRerunAwareConfiguration).getTestSpecsForRerun(request, myConsoleProperties.scope, failedTestLocations)
        }
        else {
          failedTestLocations.mapNotNull { configuration.getTestSpec(request, it.first, it.second) }
        }
      if (result.isEmpty()) {
        val locations = failedTests.map { it.locationUrl }
        LOG.warn("Can't resolve specs for the following tests: ${locations.joinToString(separator = ", ")}")
      }
      return result
    }

    private fun getTestLocations(tests: List<AbstractTestProxy>): List<Pair<Location<*>, AbstractTestProxy>> {
      val testLocations = mutableListOf<Pair<Location<*>, AbstractTestProxy>>()
      for (test in tests) {
        if (test.isLeaf) {
          val location = test.getLocation(project, myConsoleProperties.scope)
          if (location != null) {
            testLocations.add(Pair.create(location, test))
          }
        }
      }
      return testLocations
    }

    override fun addAfterParameters(cmd: GeneralCommandLine) {
      state.addAfterParameters(cmd)
    }

    override fun addBeforeParameters(cmd: GeneralCommandLine) {
      state.addBeforeParameters(cmd)
    }

    override fun addBeforeParameters(testScriptExecution: PythonScriptExecution) {
      state.addBeforeParameters(testScriptExecution)
    }

    override fun addAfterParameters(targetEnvironmentRequest: TargetEnvironmentRequest,
                                    testScriptExecution: PythonScriptExecution) {
      state.addAfterParameters(targetEnvironmentRequest, testScriptExecution)
    }

    override fun customizeEnvironmentVars(envs: Map<String, String>, passParentEnvs: Boolean) {
      super.customizeEnvironmentVars(envs, passParentEnvs)
      state.customizeEnvironmentVars(envs, passParentEnvs)
    }

    override fun customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest: HelpersAwareTargetEnvironmentRequest,
                                                         envs: Map<String, Function<TargetEnvironment, String>>,
                                                         passParentEnvs: Boolean) {
      super.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs)
      state.customizePythonExecutionEnvironmentVars(helpersAwareTargetRequest, envs, passParentEnvs)
    }
  }

  companion object {
    private val LOG = logger<PyRerunFailedTestsAction>()
  }
}