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

  val testName = "Git remote $repositoryUrl is not available: ${remoteFailure.command}"
  logOutput("$testName. The test is skipped.")

  CIServer.instance.reportTestFailure(
    testName = testName,
    message = remoteFailure.message.orEmpty(),
    details = failure.stackTraceToString(),
    kind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
  )

  TestAborter.instance.abort(testName, failure)
}

/** @return the first [GitRemoteException] in this failure, in its causes, or in its suppressed failures. */
private fun Throwable.findGitRemoteFailure(): GitRemoteException? =
  this as? GitRemoteException
  ?: cause?.findGitRemoteFailure()
  ?: suppressed.firstNotNullOfOrNull { it.findGitRemoteFailure() }
