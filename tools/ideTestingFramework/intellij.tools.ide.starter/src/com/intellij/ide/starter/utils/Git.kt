package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

object Git {
  val branch by lazy { getShortBranchName() }
  val localBranch by lazy { getLocalGitBranch() }

  @Throws(IOException::class, InterruptedException::class)
  private fun getLocalGitBranch(): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-branch-get",
      workDir = null, timeout = 1.minutes,
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  private fun getShortBranchName(): String {
    val master = "master"
    return runCatching {
      when (val branch = getLocalGitBranch().substringBefore(".")) {
        master -> return branch
        else -> when (branch.toIntOrNull()) {
          null -> return master
          else -> return "IjPlatform$branch"
        }
      }
    }.getOrElse { master }
  }

  fun getRepoRoot(): Path {
    val stdout = ExecOutputRedirect.ToString()

    try {
      ProcessExecutor(
        "git-repo-root-get",
        workDir = null, timeout = 1.minutes,
        args = listOf("git", "rev-parse", "--show-toplevel", "HEAD"),
        stdoutRedirect = stdout
      ).start()
    }
    catch (e: Exception) {
      val workDir = Paths.get("").toAbsolutePath()
      logError("There is a problem in detecting git repo root. Trying to acquire working dir path: '$workDir'")
      return workDir
    }

    // Takes first line from output like this:
    // /opt/REPO/intellij
    // 1916dc2bef46b51cfb02ad9f7e87d12aa1aa9fdc
    return Path(stdout.read().split("\n").first().trim()).toAbsolutePath()
  }
}

