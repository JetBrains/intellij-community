package com.intellij.ide.starter.ci

import java.nio.file.Path

interface CIServer {
  val isBuildRunningOnCI: Boolean
  val buildNumber: String
  val branchName: String
  val buildParams: Map<String, String>

  fun publishArtifact(source: Path,
                      artifactPath: String,
                      artifactName: String = source.fileName.toString())

  fun reportTestFailure(testName: String, message: String, details: String)
}