package com.intellij.ide.starter.ci

import com.intellij.ide.starter.utils.logError
import java.nio.file.Path

/** Dummy for CI server */
object NoCIServer : CIServer {
  override val isBuildRunningOnCI: Boolean = false
  override val buildNumber: String = ""
  override val branchName: String = ""
  override val buildParams: Map<String, String> = mapOf()

  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    logError("No logic for publishing artifacts has been implemented")
  }

  override fun reportTestFailure(testName: String, message: String, details: String) {
    logError("No logic for reporting test failure has been implemented")
  }
}