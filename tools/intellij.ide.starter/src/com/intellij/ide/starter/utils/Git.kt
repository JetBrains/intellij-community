package com.intellij.ide.starter.utils

import com.intellij.ide.starter.exec.ExecOutputRedirect
import com.intellij.ide.starter.exec.exec
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.time.Duration

object Git {
  val branch by lazy { getShortBranchName() }

  @Throws(IOException::class, InterruptedException::class)
  private fun getLocalGitBranch(): String {
    val stdout = ExecOutputRedirect.ToString()
    exec(
      "git-local-branch-get",
      workDir = null, timeout = Duration.minutes(1),
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    )
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

  fun getRepoRoot(path: Path = Paths.get("").toAbsolutePath()): Path {
    val stdout = ExecOutputRedirect.ToString()

    exec(
      "git-repo-root-get",
      workDir = null, timeout = Duration.minutes(1),
      args = listOf("git", "rev-parse", "--show-toplevel", "HEAD"),
      stdoutRedirect = stdout
    )

    // Takes first line from output like this:
    // /opt/REPO/intellij
    // 1916dc2bef46b51cfb02ad9f7e87d12aa1aa9fdc
    return Path(stdout.read().split(System.lineSeparator()).first().trim()).toAbsolutePath()
  }
}

