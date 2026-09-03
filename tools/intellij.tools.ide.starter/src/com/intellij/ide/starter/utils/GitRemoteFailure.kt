// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.runner.TestAborter
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.util.common.logOutput

/**
 * Reports [failure] as a [SyntheticTestKind.TEST_INFRA_EXCEPTION] test and skips the test, if a
 * [GitRemoteException] is anywhere in it. Returns for every other failure.
 */
internal fun abortOnUnavailableGitRemote(repositoryUrl: String, failure: Throwable) {
  val remoteFailure = failure.findGitRemoteFailure() ?: return

  val testName = "Git remote ${gitRepositoryName(repositoryUrl)} is not available: ${remoteFailure.command}"
  logOutput("The git remote $repositoryUrl is not available. The test is skipped.")

  CIServer.instance.reportTestFailure(
    testName = testName,
    message = "The git remote $repositoryUrl is not available. ${remoteFailure.message.orEmpty()}",
    details = failure.stackTraceToString(),
    kind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
    // The name holds no volatile part, and `generifyErrorMessage` would replace the repository with `<FILE>`.
    generifyTestName = false,
  )

  TestAborter.instance.abort(testName, failure)
}

/**
 * @return the path of the repository at [repoUrl], without the scheme, the host and the `.git` suffix.
 * For example, `https://github.com/DataGrip/dumps.git` gives `DataGrip/dumps`. The name of a test must
 * hold this instead of the url, because `generifyErrorMessage` replaces a url with `<LINK>`.
 */
internal fun gitRepositoryName(repoUrl: String): String {
  val afterScheme = repoUrl.substringAfter("://")
  // A host ends with the first `/`. It ends with the first `:` in a url such as `git@github.com:owner/repo`.
  val hostEnd = afterScheme.indexOfFirst { it == '/' || it == ':' }
  return afterScheme.substring(hostEnd + 1).trim('/').removeSuffix(".git")
}

/** @return the first [GitRemoteException] in this failure, in its causes, or in its suppressed failures. */
private fun Throwable.findGitRemoteFailure(): GitRemoteException? =
  this as? GitRemoteException
  ?: cause?.findGitRemoteFailure()
  ?: suppressed.firstNotNullOfOrNull { it.findGitRemoteFailure() }
