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
 * and reconstructed into a `-Dtest=Class1#method1+method2,Class2#method3` Surefire filter.
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
    val specs = allTests.filter { it.isLeaf && it.isDefect }
      .mapNotNull { proxy ->
        // Use the locationHint from the proxy (or its parent) to build the Surefire spec.
        // The hint format is "java:test://ClassName/methodName", which always carries the
        // full class and bare method regardless of what the display name looks like.
        surefireSpecFromLocation(proxy.locationUrl)
          ?: surefireSpecFromLocation(proxy.parent?.locationUrl)
      }
    return groupSpecsForSurefire(specs)
  }
}

/**
 * Converts a `java:test://ClassName/methodName` location URL to a Surefire test spec
 * (`ClassName#methodName`). Returns null for any other URL scheme or malformed input.
 *
 * This is the primary way [SurefireRerunFailedTestsAction] builds the `-Dtest=` filter,
 * because the location URL is independent of the display name format.
 */
@ApiStatus.Internal
fun surefireSpecFromLocation(locationUrl: String?): String? {
  if (locationUrl == null || !locationUrl.startsWith("java:test://")) return null
  val path = locationUrl.removePrefix("java:test://")
  val slash = path.indexOf('/')
  if (slash < 0) return null
  val className = path.substring(0, slash)
  val methodName = path.substring(slash + 1)
  if (className.isEmpty() || methodName.isEmpty()) return null
  return "$className#$methodName"
}

/**
 * Groups a list of `ClassName#methodName` Surefire specs and formats them into a single
 * `-Dtest=` parameter value.
 *
 * Maven Surefire uses two separators in the `-Dtest=` value:
 * - `,` between different class specifications
 * - `+` between method names within the same class specification
 *
 * For example, two failing methods in `BasicTests` must be expressed as
 * `BasicTests#test1+test2`, not `BasicTests#test1+BasicTests#test2`.
 * The latter is parsed as class `BasicTests` with methods `test1` and `BasicTests#test2`,
 * so only `test1` runs.
 *
 * Returns null when [specs] is empty.
 */
@ApiStatus.Internal
fun groupSpecsForSurefire(specs: Iterable<String>): String? {
  val methodsByClass = LinkedHashMap<String, LinkedHashSet<String>>()
  for (spec in specs) {
    val sharp = spec.indexOf('#')
    if (sharp < 0) {
      methodsByClass.getOrPut(spec) { linkedSetOf() }
    }
    else {
      methodsByClass.getOrPut(spec.substring(0, sharp)) { linkedSetOf() }
        .add(spec.substring(sharp + 1))
    }
  }
  if (methodsByClass.isEmpty()) return null
  // Surefire -Dtest= syntax: comma between class specs, + between methods of the same class.
  return methodsByClass.entries.joinToString(",") { (cls, methods) ->
    if (methods.isEmpty()) cls else "$cls#${methods.joinToString("+")}"
  }
}

/**
 * Converts a SM runner display name to a Surefire test spec (`ClassName#methodName`).
 * Returns null when the name contains no dot (e.g. a short method name or a
 * parameterized-leaf name like `[1]`).
 *
 * The method part may carry a parameterized suffix such as `(String)[1] hello`.
 * Surefire accepts only the base method name in its `-Dtest=` filter, so the suffix
 * is stripped before building the spec.
 *
 * Note: [SurefireRerunFailedTestsAction] uses [surefireSpecFromLocation] instead, which
 * is more reliable. This function is kept for callers that only have a display name.
 */
@ApiStatus.Internal
fun surefireTestSpec(displayName: String): String? {
  val dot = displayName.lastIndexOf('.')
  if (dot < 0) return null
  val classPart = displayName.substring(0, dot)
  val rawMethod = displayName.substring(dot + 1)
  // Strip the parameterized suffix: "(Type)[index] value" or "[index] value".
  val methodName = rawMethod.substringBefore('(').substringBefore('[').trimEnd()
  if (methodName.isEmpty()) return null
  return "$classPart#$methodName"
}
