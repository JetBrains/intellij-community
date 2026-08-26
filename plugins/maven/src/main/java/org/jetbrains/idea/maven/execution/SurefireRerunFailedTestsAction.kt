// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.ComponentContainer
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<SurefireRerunFailedTestsAction>()

/**
 * Reruns only the tests that failed in the last Surefire run.
 *
 * The failed test names are read from the SM runner model (populated by [SurefireReportParser])
 * and reconstructed into a `-Dtest=Class1#method1+Class2#method2` Surefire filter.
 *
 * We override [actionPerformed] rather than [getRunProfile] to avoid the [MyRunProfile] wrapper
 * that [AbstractRerunFailedTestsAction] normally uses: [MavenResumeAction] hard-casts
 * [ExecutionEnvironment.getRunProfile] to [MavenRunConfiguration], so the wrapper cannot be in
 * the environment's profile slot.
 */
internal class SurefireRerunFailedTestsAction(
  componentContainer: ComponentContainer,
  private val configuration: MavenSurefireRunConfiguration,
) : AbstractRerunFailedTestsAction(componentContainer) {

  override fun actionPerformed(e: AnActionEvent) {
    val environment = e.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT) ?: return
    val model = model ?: run { LOG.debug("SurefireRerunFailedTestsAction: SM runner model not yet available"); return }
    val failedTestParam = buildFailedTestParam(model.root.allTests) ?: return

    val cloned = configuration.clone() as MavenSurefireRunConfiguration
    val goals = ArrayList(cloned.runnerParameters.goals)
    val idx = goals.indexOfFirst { it.startsWith("-Dtest=") }
    if (idx >= 0) goals[idx] = "-Dtest=$failedTestParam" else goals.add("-Dtest=$failedTestParam")
    cloned.runnerParameters.setGoals(goals)

    try {
      // Pass the cloned MavenSurefireRunConfiguration directly — not a MyRunProfile wrapper —
      // so MavenResumeAction can cast environment.runProfile to MavenRunConfiguration safely.
      val newEnv = ExecutionEnvironmentBuilder(environment).runProfile(cloned).build()
      environment.runner.execute(newEnv)
    }
    catch (ex: ExecutionException) {
      LOG.warn("Failed to rerun failed Surefire tests", ex)
    }
  }

  private fun buildFailedTestParam(allTests: List<AbstractTestProxy>): String? {
    val specs = allTests.filter { it.isLeaf && it.isDefect }.mapNotNull { surefireTestSpec(it.name) }
    return specs.ifEmpty { null }?.joinToString("+")
  }
}

/**
 * Converts a SM runner display name (`ClassName.methodName` as written by [SurefireReportParser])
 * to a Surefire test spec (`ClassName#methodName`). Returns null when the name contains no dot
 * (e.g. a suite node rather than a leaf test).
 */
@ApiStatus.Internal
fun surefireTestSpec(displayName: String): String? {
  val dot = displayName.lastIndexOf('.')
  return if (dot < 0) null else "${displayName.substring(0, dot)}#${displayName.substring(dot + 1)}"
}
