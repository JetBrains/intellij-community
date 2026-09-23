package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind

internal fun BackgroundRun.formatForRecoveryLog(): String {
  val processes = ownedProcesses.joinToString("; ") { "pid=${it.id} alive=${it.isAlive}" }
  return "BackgroundRun@${System.identityHashCode(this).toString(16)}(processes=[$processes], " +
         "launchName='${runContext.lastIdeReportingData.humanReadableTestName}')"
}

internal fun SettingsRecoveryPlan.formatForRecoveryLog(): String {
  return "requiresRestart=$requiresRestart, changed=${changedFileSpecs.formatFileSpecsForRecoveryLog()}, " +
         "deleted=${deletedFileSpecs.formatFileSpecsForRecoveryLog()}"
}

private fun Set<String>.formatFileSpecsForRecoveryLog(): String {
  return sorted().joinToString(prefix = "[", postfix = "]")
}

/** Tells CI that recovery could not restore the reused IDE state without restarting the IDE. */
internal fun reportRecoveryRestartRequired(
  reason: String,
  details: String,
  previousArtifacts: IDEReportingData,
  newArtifacts: IDEReportingData,
) {
  val message = "Reused IDE recovery could not restore reused IDE state without restarting the IDE"
  CIServer.instance.reportTestFailure(
    testName = "$message: ${previousArtifacts.humanReadableTestName}",
    generifyTestName = false,
    kind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
    details = details,
    message = "IDE launch that required restart recovery: [${previousArtifacts.humanReadableTestName}]\n" +
              "Restarted IDE launch: [${newArtifacts.humanReadableTestName}]\n" +
              "$message.\n" +
              "Reason: $reason\n\n" +
              "Please investigate which IDE state was changed by the test. " +
              "Reused recovery expects tests to leave only reloadable settings and restorable project state.",
  )
}
