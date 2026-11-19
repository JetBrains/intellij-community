// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter

import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.Git
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * A parent git, such as `git rebase --exec`, exports `GIT_DIR` and its kin to the test JVM.
 * The git of the starter must still write only the repository of its working directory.
 */
class GitEnvironmentTest {
  @Test
  fun `the local git environment drops the variables of a parent git`() {
    val parent = mapOf("PATH" to "/usr/bin", "GIT_DIR" to "/decoy/.git", "GIT_WORK_TREE" to "/decoy", "GIT_SSH_COMMAND" to "ssh")

    Git.localGitEnvironment(parent) shouldBe mapOf("PATH" to "/usr/bin", "GIT_SSH_COMMAND" to "ssh")
  }

  @Test
  fun `a git under the local git environment leaves the repository of the parent git alone`(@TempDir root: Path) {
    val decoy = root.resolve("decoy").createDirectories()
    val own = root.resolve("own").createDirectories()
    git(decoy, System.getenv(), "init", "-q")
    git(decoy, System.getenv(), "remote", "add", "origin", "https://example.com/decoy.git")
    val before = decoy.resolve(".git/config").readText()

    val inherited = System.getenv() + mapOf("GIT_DIR" to decoy.resolve(".git").toString(), "GIT_WORK_TREE" to decoy.toString())
    git(own, Git.localGitEnvironment(inherited), "init", "-q")
    git(own, Git.localGitEnvironment(inherited), "remote", "add", "origin", "https://example.com/own.git")

    decoy.resolve(".git/config").readText() shouldBe before
    Git.getRemoteUrl(own, "origin") shouldBe "https://example.com/own.git"
  }

  private fun git(dir: Path, environment: Map<String, String>, vararg args: String) {
    ProcessExecutor(presentableName = "git-${args.first()}", workDir = dir, environmentVariables = environment, args = listOf("git", *args)).start()
  }
}
