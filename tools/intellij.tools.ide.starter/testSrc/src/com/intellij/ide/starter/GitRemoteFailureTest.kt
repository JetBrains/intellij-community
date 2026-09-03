// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.TestAborter
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.GitRemoteException
import com.intellij.ide.starter.utils.abortOnUnavailableGitRemote
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import org.opentest4j.TestAbortedException
import java.nio.file.Path

private const val REPOSITORY_URL = "https://github.com/jitpack/android-example.git"

/** Does what `JUnit5TestAborter` does, without a dependency on that module. */
private object AbortingTestAborter : TestAborter {
  override fun abort(message: String, cause: Throwable): Nothing = throw TestAbortedException(message, cause)
}

class GitRemoteFailureTest {

  private lateinit var originalDi: DI

  @BeforeEach
  fun bindTestAborter() {
    originalDi = di
    di = DI {
      extend(originalDi)
      bindSingleton<TestAborter>(overrides = true) { AbortingTestAborter }
    }
  }

  @AfterEach
  fun restoreDi() {
    di = originalDi
  }

  @Test
  fun `a failure of a remote command skips the test`() {
    val failure = GitRemoteException("git-fetch", cause = IllegalStateException("exit code 128"))

    val reported = failuresReportedWhile {
      val aborted = shouldThrow<TestAbortedException> { abortOnUnavailableGitRemote(REPOSITORY_URL, failure) }
      aborted.cause shouldBe failure
    }

    reported.single().kind shouldBe SyntheticTestKind.TEST_INFRA_EXCEPTION
    reported.single().testName shouldContain REPOSITORY_URL
    reported.single().testName shouldContain "git-fetch"
    reported.single().details shouldContain "GitRemoteException"
  }

  @Test
  fun `a failure of a remote command in a cause skips the test`() {
    val failure = IllegalStateException("Failed to setup the test project", GitRemoteException("git-pull"))

    val reported = failuresReportedWhile {
      shouldThrow<TestAbortedException> { abortOnUnavailableGitRemote(REPOSITORY_URL, failure) }
    }

    reported.single().testName shouldContain "git-pull"
  }

  @Test
  fun `a failure of a remote command in a suppressed failure skips the test`() {
    val failure = IllegalStateException("destination path already exists")
    failure.addSuppressed(GitRemoteException("git-clone"))

    val reported = failuresReportedWhile {
      shouldThrow<TestAbortedException> { abortOnUnavailableGitRemote(REPOSITORY_URL, failure) }
    }

    reported.single().testName shouldContain "git-clone"
  }

  @Test
  fun `a failure of a local command stays a test failure`() {
    val failure = IllegalStateException("error: pathspec 'no-such-branch' did not match any file(s) known to git")

    val reported = failuresReportedWhile { abortOnUnavailableGitRemote(REPOSITORY_URL, failure) }

    reported.shouldBeEmpty()
  }

  @Test
  fun `git marks the failure of a command that talks to a remote`(@TempDir repository: Path) {
    Git.init(repository)
    Git.addRemote(repository, "origin", repository.resolve("no-such-repository.git").toString())

    val failure = shouldThrow<GitRemoteException> { Git.fetch(repository) }

    failure.command shouldBe "git-fetch"
  }

  @Test
  fun `git leaves the failure of a local command alone`(@TempDir repository: Path) {
    Git.init(repository)

    shouldThrow<IllegalStateException> { Git.reset(repository, commitHash = "no-such-ref") }
  }
}
