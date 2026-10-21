// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.Git
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.opentest4j.AssertionFailedError
import kotlin.time.Duration.Companion.minutes

/**
 * The SSH URLs of the test projects. Each host gets a public and a private repository, because a host
 * lets any known key read a public repository, but only a key with access can read a private one.
 */
private val SSH_REPOSITORIES = listOf(
  "ssh://git@github.com/junit-team/junit5.git",
  "ssh://git@github.com/DataGrip/dumps.git",
  "ssh://git@git.jetbrains.team/platform-test-automation/platform-test-projects.git",
  "ssh://git@git.jetbrains.team/intellij.git",
)

/**
 * An agent holds one SSH key per host, and the SSH agent offers all of them to each host. A host takes the
 * first key that it knows. That key can belong to an account with no access to the repository, so each
 * repository needs its own check.
 *
 * On TeamCity, a repository that this machine cannot read fails the test. On any other machine, it skips
 * the test, because a developer machine can have no key for a host.
 */
class GitSshAccessTest {

  @TestFactory
  fun `each repository is readable over SSH`(): List<DynamicTest> = SSH_REPOSITORIES.map { repositoryUrl ->
    dynamicTest(repositoryUrl) {
      val failure = lsRemote(repositoryUrl) ?: return@dynamicTest
      val message = "This machine cannot read $repositoryUrl over SSH.\n$failure\n${Git.sshIdentityReport(repositoryUrl)}"

      assumeTrue(isBuildRunningOnTeamCity(), message)
      throw AssertionFailedError(message)
    }
  }
}

/** `TeamCityCIServer` reads the same variable. The test does not use it, because the DI of a unit test binds `NoCIServer`. */
private fun isBuildRunningOnTeamCity(): Boolean = System.getenv("TEAMCITY_VERSION") != null

/**
 * Runs `git ls-remote` for the `HEAD` of [repositoryUrl]. The command needs no checkout, so it stays fast.
 * Git and SSH ask for no credential, so the command cannot block.
 *
 * @return `null` when the command succeeds, or the output of git when it fails.
 */
private fun lsRemote(repositoryUrl: String): String? {
  // Keep the ssh command of the build, because it can hold options that the host needs.
  val sshCommand = System.getenv("GIT_SSH_COMMAND")?.takeIf { it.isNotBlank() } ?: "ssh"
  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()
  val result = runCatching {
    ProcessExecutor(
      presentableName = "git-ls-remote",
      workDir = null,
      timeout = 2.minutes,
      args = listOf("git", "ls-remote", "--exit-code", repositoryUrl, "HEAD"),
      environmentVariables = mapOf("GIT_TERMINAL_PROMPT" to "0", "GIT_SSH_COMMAND" to "$sshCommand -o BatchMode=yes"),
      onlyEnrichExistedEnvVariables = true,
      stdoutRedirect = stdout,
      stderrRedirect = stderr,
    ).start()
  }
  return result.exceptionOrNull()?.let { "The output of `git ls-remote`:\n${stderr.read()}${stdout.read()}".trimEnd() }
}
