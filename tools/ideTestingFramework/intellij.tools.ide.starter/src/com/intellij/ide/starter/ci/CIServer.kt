// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.ci

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

  fun reportTestFailure(testName: String, message: String, details: String)
}