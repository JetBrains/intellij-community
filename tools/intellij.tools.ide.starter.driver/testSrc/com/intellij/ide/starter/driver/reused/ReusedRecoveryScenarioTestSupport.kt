package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Remote
import com.intellij.ide.starter.junit5.CurrentTestMethodProvider
import com.intellij.ide.starter.runner.CurrentTestMethod
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherConfig
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

fun executeReusedIdeRecoveryScenario(testClass: Class<*>): TestExecutionSummary {
  return executeJUnitClass(testClass)
}

internal fun assertInitialAndRestartedIdeaLogsSaved(testHome: Path) {
  val ideaLogs = collectIdeaLogs(testHome)
  assertTrue(
    ideaLogs.size >= 2,
    "Expected logs from initial and restarted IDE runs under $testHome. Found idea.log files: ${ideaLogs.joinToString()}",
  )
}

/**
 * Executes a recovery scenario through a nested JUnit launcher and returns its summary to the wrapper test.
 *
 * Recovery scenario classes are marked with `@Disabled` so accidental top-level JUnit discovery does not run their
 * intentionally failing first tests. Wrapper execution deactivates only JUnit's disabled condition for this launcher request.
 *
 * The nested launcher must not auto-register service-loaded `TestExecutionListener`s. The outer test run has already
 * initialized Starter infrastructure; registering those listeners again creates a second telemetry lifecycle in the
 * same JVM and can report background exporter failures against the wrapper test after the nested summary is asserted.
 */
private fun executeJUnitClass(testClass: Class<*>): TestExecutionSummary {
  val launcher = LauncherFactory.create(
    LauncherConfig.builder()
      .enableLauncherSessionListenerAutoRegistration(false)
      .enableTestExecutionListenerAutoRegistration(false)
      .build()
  )
  val requestBuilder = LauncherDiscoveryRequestBuilder.request()
    .selectors(selectClass(testClass))
    .configurationParameter(JUNIT_CONDITIONS_DEACTIVATE_PROPERTY, JUNIT_DISABLED_CONDITION_PATTERN)

  val request = requestBuilder.build()
  val summaryListener = SummaryGeneratingListener()
  val currentTestMethod = CurrentTestMethod.get()
  launcher.registerTestExecutionListeners(CurrentTestMethodProvider(), summaryListener)
  try {
    launcher.execute(request)
    return summaryListener.summary
  }
  finally {
    CurrentTestMethod.set(currentTestMethod)
  }
}

private fun collectIdeaLogs(testHome: Path): List<Path> {
  if (!testHome.exists()) {
    return emptyList()
  }
  return Files.find(testHome, 8, { path, _ -> path.fileName?.toString() == "idea.log" }).use { stream ->
    stream.map { it.toAbsolutePath().normalize() }
      .sorted()
      .toList()
  }
}

private const val JUNIT_CONDITIONS_DEACTIVATE_PROPERTY = "junit.jupiter.conditions.deactivate"
private const val JUNIT_DISABLED_CONDITION_PATTERN = "org.junit.*DisabledCondition"

@Remote("com.intellij.openapi.application.PathManager")
internal interface PathManagerRemote {
  fun getConfigPath(): String
  fun getSystemPath(): String
}
