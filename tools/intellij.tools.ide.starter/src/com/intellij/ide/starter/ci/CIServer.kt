package com.intellij.ide.starter.ci

import com.intellij.ide.starter.di.di
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

//TODO(Describe different approaches for different CIs about publishing artifacts)
interface CIServer {
  val isBuildRunningOnCI: Boolean
  val buildNumber: String
  val branchName: String
  val buildParams: Map<String, String>

  fun publishArtifact(source: Path,
                      artifactPath: String,
                      artifactName: String = source.fileName.toString())

  fun reportTestFailure(testName: String, message: String, details: String, linkToLogs: String? = null)

  fun ignoreTestFailure(testName: String, message: String, details: String? = null)

  fun isTestFailureShouldBeIgnored(message: String): Boolean

  companion object {
    val instance: CIServer
      get() = di.direct.instance<CIServer>()
  }
}