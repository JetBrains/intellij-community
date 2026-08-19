package com.intellij.ide.starter

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path

/** A failure a [CIServer] was asked to report. */
internal data class ReportedFailure(val testName: String, val message: String, val details: String, val kind: SyntheticTestKind)

/**
 * The failures reported while [body] runs, and whether the run they were reported from claimed to be on CI — the two things that decide
 * whether a path too long for Windows is anyone's problem. The CI server of the rest of the run is left as it was.
 */
internal fun failuresReportedWhile(isBuildRunningOnCI: Boolean = false, body: () -> Unit): List<ReportedFailure> {
  val ciServer = RecordingCIServer(isBuildRunningOnCI)
  val originalDi = di
  di = DI {
    extend(originalDi)
    bindSingleton<CIServer>(overrides = true) { ciServer }
  }
  try {
    body()
  }
  finally {
    di = originalDi
  }
  return ciServer.reportedFailures
}

/** A [CIServer] that keeps what it is told instead of reporting it anywhere. */
private class RecordingCIServer(override val isBuildRunningOnCI: Boolean) : CIServer {
  val reportedFailures: MutableList<ReportedFailure> = mutableListOf()

  override val buildNumber: String = ""
  override val branchName: String = ""
  override val buildParams: Map<String, String> = emptyMap()

  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) = Unit

  override fun reportTestFailure(
    testName: String, message: String, details: String, linkToLogs: String?,
    kind: SyntheticTestKind, generifyTestName: Boolean,
  ) {
    reportedFailures += ReportedFailure(testName, message, details, kind)
  }

  override fun ignoreTestFailure(testName: String, message: String, details: String?, kind: SyntheticTestKind) = Unit

  override fun isTestFailureShouldBeIgnored(message: String): Boolean = false
}
