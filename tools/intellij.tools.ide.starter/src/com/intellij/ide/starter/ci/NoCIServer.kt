package com.intellij.ide.starter.ci

import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.TestMetadata
import com.intellij.tools.ide.util.common.logError
import java.nio.file.Path

/** Dummy for CI server */
object NoCIServer : CIServer {
  override val isBuildRunningOnCI: Boolean = false
  override val buildNumber: String = ""
  override val branchName: String = ""
  override val buildParams: Map<String, String> = mapOf()

  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    logError("""
      No logic for publishing artifacts has been implemented.
      If you want to publish artifacts somewhere (e.g. in CI build) - implement [CIServer] interface and register it via KodeinDI as specified in Readme.
      """.trimIndent())
  }

  override fun reportTestFailure(
    testName: String, message: String, details: String, linkToLogs: String?,
    kind: SyntheticTestKind, generifyTestName: Boolean, additionalMetadata: List<TestMetadata>,
  ) {
    logError("""
      No logic for reporting test failure has been implemented.
      If you want to report tests failures (e.g. on CI) - implement [CIServer] interface and register it via KodeinDI as specified in Readme.
      """.trimIndent())
  }

  override fun ignoreTestFailure(
    testName: String, message: String, details: String?,
    kind: SyntheticTestKind,
  ) {
    logError("""
      No logic for ignoring test failure has been implemented.
      If you want to ignore tests failures (e.g. on CI) - implement [CIServer] interface and register it via KodeinDI as specified in Readme.
      """.trimIndent())
  }

  override fun isTestFailureShouldBeIgnored(message: String): Boolean {
    return false
  }
}