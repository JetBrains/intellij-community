// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComponentContainer

/**
 * Reruns only the tests that failed in the last Surefire run.
 *
 * The failed test names are read from the SM runner model (populated by [SurefireReportParser])
 * and reconstructed into a `-Dtest=Class1#method1+Class2#method2` Surefire filter.
 */
internal class SurefireRerunFailedTestsAction(
  componentContainer: ComponentContainer,
  private val configuration: MavenSurefireRunConfiguration,
) : AbstractRerunFailedTestsAction(componentContainer) {

  override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
    val model = model ?: return null
    val failedTestParam = buildFailedTestParam(model.root.allTests) ?: return null

    val cloned = configuration.clone() as MavenSurefireRunConfiguration
    val goals = ArrayList(cloned.runnerParameters.goals)
    val idx = goals.indexOfFirst { it.startsWith("-Dtest=") }
    if (idx >= 0) goals[idx] = "-Dtest=$failedTestParam" else goals.add("-Dtest=$failedTestParam")
    cloned.runnerParameters.setGoals(goals)

    return object : MyRunProfile(cloned) {
      override fun getState(executor: com.intellij.execution.Executor, env: ExecutionEnvironment): RunProfileState? =
        cloned.getState(executor, env)

      override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        cloned.configurationEditor
    }
  }

  private fun buildFailedTestParam(allTests: List<AbstractTestProxy>): String? {
    val specs = allTests.filter { it.isLeaf && it.isDefect }.mapNotNull { proxy ->
      // displayName is "ClassName.methodName" as written by SurefireReportParser
      val name = proxy.name
      val dot = name.lastIndexOf('.')
      if (dot < 0) null else "${name.substring(0, dot)}#${name.substring(dot + 1)}"
    }
    return specs.ifEmpty { null }?.joinToString("+")
  }
}
