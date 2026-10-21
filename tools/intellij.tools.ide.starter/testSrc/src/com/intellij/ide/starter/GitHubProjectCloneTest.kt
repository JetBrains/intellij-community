// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.utils.GitRemoteException
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.minutes

private const val REPOSITORY = "DataGrip/dumps"
private const val BRANCH = "master"

/**
 * `DataGrip/dumps` is a private repository. An agent holds an SSH key for github.com, so the SSH URL of
 * [GitHubProject.fromGithubOverSsh] reaches it. The HTTPS URL of [GitHubProject.fromGithub] needs a
 * credential that no agent holds, so it reaches nothing.
 *
 * Each clone test needs the network and an account with access, so each one states an assumption first.
 * A machine without the access skips the test instead of failing it.
 */
class GitHubProjectCloneTest {

  @Test
  fun `each factory method builds the URL of its own protocol`() {
    GitHubProject.fromGithub(repoRelativeUrl = REPOSITORY).repositoryUrl shouldBe "https://github.com/DataGrip/dumps.git"
    GitHubProject.fromGithubOverSsh(repoRelativeUrl = REPOSITORY).repositoryUrl shouldBe "ssh://git@github.com/DataGrip/dumps.git"
  }

  /** Some test projects give the repository path with a leading slash, such as `/urfave/cli`. */
  @Test
  fun `a leading slash gives the same URL`() {
    GitHubProject.fromGithub(repoRelativeUrl = "/$REPOSITORY").repositoryUrl shouldBe "https://github.com/DataGrip/dumps.git"
    GitHubProject.fromGithubOverSsh(repoRelativeUrl = "/$REPOSITORY").repositoryUrl shouldBe "ssh://git@github.com/DataGrip/dumps.git"
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  fun `the SSH URL checks out the project`(@TempDir cache: Path) {
    val project = GitHubProject.fromGithubOverSsh(branchName = BRANCH, repoRelativeUrl = REPOSITORY).copy(shallow = true)

    assumeTrue(isReadable(project.repositoryUrl), "This machine holds no SSH key with access to $REPOSITORY.")

    val projectPath = withProjectCacheIn(cache) { project.downloadAndUnpackProject() }

    projectPath.shouldExist()
    projectPath.resolve(".git").shouldExist()
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  fun `the HTTPS URL reaches no private repository`(@TempDir cache: Path) {
    val project = GitHubProject.fromGithub(branchName = BRANCH, repoRelativeUrl = REPOSITORY).copy(shallow = true)

    assumeFalse(isReadable(project.repositoryUrl), "This machine holds an HTTPS credential with access to $REPOSITORY.")

    // `NoTestAborter` throws the cause, because no aborter of a test framework is on this classpath.
    // A run with `JUnit5TestAborter` skips the test instead.
    val reported = failuresReportedWhile {
      val failure = shouldThrow<GitRemoteException> { withProjectCacheIn(cache) { project.downloadAndUnpackProject() } }
      failure.command shouldBe "git-clone"
    }

    reported.single().kind shouldBe SyntheticTestKind.TEST_INFRA_EXCEPTION
    reported.single().testName shouldContain REPOSITORY
  }

  /**
   * Runs [body] with every project checkout under [cache]. [GitHubProject] maps both URLs of one repository
   * to one directory, so a shared cache would let the clone of one test answer the other one.
   */
  private fun <T> withProjectCacheIn(cache: Path, body: () -> T): T {
    val originalDi = di
    di = DI {
      extend(originalDi)
      bindSingleton<GlobalPaths>(overrides = true) { CachedProjectPaths(cache) }
    }
    try {
      return body()
    }
    finally {
      di = originalDi
    }
  }
}

/**
 * A [GlobalPaths] that keeps each project checkout under [root].
 *
 * [com.intellij.ide.starter.project.GitProjectInfo] clones into [localCacheDirectory] and returns a path in [cacheDirForProjects].
 * The second one derives from the first one, so this class overrides only the first one.
 */
private class CachedProjectPaths(private val root: Path) : GlobalPaths(root) {
  override val localCacheDirectory: Path get() = root.createDirectories()
}

/**
 * This machine can read the repository at [repositoryUrl]. `git ls-remote` needs no checkout, and it asks
 * for no credential, so the probe stays fast and cannot block.
 */
private fun isReadable(repositoryUrl: String): Boolean = runCatching {
  ProcessExecutor(
    presentableName = "git-ls-remote",
    workDir = null,
    timeout = 2.minutes,
    args = listOf("git", "ls-remote", "--heads", repositoryUrl),
    environmentVariables = mapOf("GIT_TERMINAL_PROMPT" to "0", "GIT_SSH_COMMAND" to "ssh -o BatchMode=yes"),
    onlyEnrichExistedEnvVariables = true,
    stdoutRedirect = ExecOutputRedirect.ToString(),
    stderrRedirect = ExecOutputRedirect.ToString(),
  ).start()
}.isSuccess