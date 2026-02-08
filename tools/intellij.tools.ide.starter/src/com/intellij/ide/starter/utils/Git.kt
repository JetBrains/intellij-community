package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.SetupException
import com.intellij.openapi.application.PathManager
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Random
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Git {
  private val homeDir: Path by lazy { Paths.get(PathManager.getHomePath()) }
  val branch by lazy { getShortBranchName(homeDir) }
  val localBranch by lazy { getLocalGitBranch(homeDir) }
  val getDefaultBranch by lazy {
    when (val majorBranch = localBranch.substringBefore(".")) {
      "HEAD", "master" -> "master"
      else -> majorBranch
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getLocalGitBranch(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-branch-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getLocalCurrentCommitHash(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-local-current-commit-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "HEAD"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  private fun getShortBranchName(repositoryDirectory: Path): String {
    val master = "master"
    return runCatching {
      when (val branch = getLocalGitBranch(repositoryDirectory).substringBefore(".")) {
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
        workDir = null,
        timeout = 1.minutes,
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

  @OptIn(ExperimentalPathApi::class)
  fun clone(repoUrl: String, destinationDir: Path, branchName: String = "", shallow: Boolean, withSubmodules: Boolean = false, timeout: Duration = 10.minutes) {
    val cmdName = "git-clone"

    val arguments = mutableListOf("git", "clone", repoUrl, destinationDir.toString())
    if (branchName.isNotEmpty()) arguments.addAll(listOf("-b", branchName))
    if (shallow) arguments.addAll(listOf("--depth", "1"))
    if (withSubmodules) arguments.add("--recurse-submodules")

    withRetryBlocking("Git clone $repoUrl failed", rollback = {
      logOutput("Deleting $destinationDir ...")

      withRetry("Failed to delete $destinationDir") { destinationDir.deleteRecursively() }
    }) {
      ProcessExecutor(
        presentableName = cmdName,
        workDir = destinationDir.parent.toAbsolutePath(),
        timeout = timeout,
        args = arguments,
        stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
        stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
        onlyEnrichExistedEnvVariables = true
      ).start()
    } ?: throw SetupException("Git clone $repoUrl failed")
  }

  fun status(projectDir: Path): Int {
    val arguments = mutableListOf("git", "-c", "core.fsmonitor=false", "status")

    val startTimer = System.currentTimeMillis()

    val execOutStatus = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "git-status",
      workDir = projectDir,
      timeout = 2.minutes,
      args = arguments,
      stdoutRedirect = execOutStatus,
      stderrRedirect = ExecOutputRedirect.ToString(),
      onlyEnrichExistedEnvVariables = true
    ).start()

    val endTimer = System.currentTimeMillis()
    val duration = (endTimer - startTimer).toInt()
    println("Git status took $duration")
    println("Git status output: ${execOutStatus.read()}")

    val execOutVersion = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "git-version",
      workDir = projectDir,
      timeout = 1.minutes,
      args = listOf("git", "--version"),
      stdoutRedirect = execOutVersion,
      stderrRedirect = ExecOutputRedirect.ToString(),
      onlyEnrichExistedEnvVariables = true
    ).start()

    println("Git version: ${execOutVersion.read()}")

    val execOutConfig = ExecOutputRedirect.ToString()
    ProcessExecutor(
      presentableName = "git-config",
      workDir = projectDir,
      timeout = 1.minutes,
      args = listOf("git", "config", "-l", "--show-scope"),
      stdoutRedirect = execOutConfig,
      stderrRedirect = ExecOutputRedirect.ToString(),
      onlyEnrichExistedEnvVariables = true
    ).start()

    println("Git config: ${execOutConfig.read()}")
    return duration
  }

  fun ensureRepository(repositoryDirectory: Path) {
    val cmdName = "git-rev-parse-show-prefix"
    val arguments = mutableListOf("git", "rev-parse", "--show-prefix")
    val stdoutRedirect = ExecOutputRedirect.ToString()
    val stderrRedirect = ExecOutputRedirect.ToStdOutAndString("[$cmdName]")
    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.seconds,
      args = arguments,
      stdoutRedirect = stdoutRedirect,
      stderrRedirect = stderrRedirect,
      onlyEnrichExistedEnvVariables = true
    ).start()
    if (stdoutRedirect.read().trim().isNotEmpty()) {
      error("Not a root of a git repository: $repositoryDirectory. $cmdName output: ${stdoutRedirect.read()}")
    }
  }

  fun reset(repositoryDirectory: Path, commitHash: String = "") {
    ensureRepository(repositoryDirectory)

    val cmdName = "git-reset"

    val arguments = mutableListOf("git", "reset", "--hard")
    if (commitHash.isNotEmpty()) arguments.add(commitHash)

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun clean(repositoryDirectory: Path) {
    val cmdName = "git-clean"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "clean", "-fd"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }


  fun checkout(repositoryDirectory: Path, branchName: String = "") {
    val cmdName = "git-checkout"

    val arguments = mutableListOf("git", "checkout")
    if (branchName.isNotEmpty()) arguments.add(branchName)

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun fetch(repositoryDirectory: Path) {
    val cmdName = "git-fetch"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "fetch"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun pull(repositoryDirectory: Path) {
    val cmdName = "git-pull"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "pull"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun rebase(repositoryDirectory: Path, newBase: String = "master") {
    val cmdName = "git-rebase"

    ProcessExecutor(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "rebase", newBase),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      onlyEnrichExistedEnvVariables = true
    ).start()
  }

  fun deleteBranch(workDir: Path, targetBranch: String) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-delete-branch",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "branch", "-D", targetBranch),
      stdoutRedirect = stdout
    ).start()
  }

  fun init(workDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-init",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "init"),
      stdoutRedirect = stdout
    ).start()
  }

  fun addAll(workDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-add-all",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "add", "*"),
      stdoutRedirect = stdout
    ).start()
  }

  fun pruneWorktree(pathToDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-prune-worktree",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "worktree", "prune"),
      stdoutRedirect = stdout
    ).start()
  }

  fun getStatus(pathToDir: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-status",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "status"),
      stdoutRedirect = stdout
    ).start()
    return stdout.read()
  }

  fun getDiff(pathToDir: Path, diffStart: String, diffEnd: String): String {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git-diff",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "diff", diffStart, diffEnd, "--unified=0", "--no-renames"),
      stdoutRedirect = stdout
    ).start()
    return stdout.read()
  }

  /**
   * If commitHash is specified, only branches with this commit will be returned.
   * */
  fun getLocalBranches(repositoryDirectory: Path, commitHash: String = ""): List<String> {
    val arguments = mutableListOf("git", "for-each-ref", "--format='%(refname:short)'", "refs/heads/")
    if (commitHash.isNotEmpty()) arguments.addAll(listOf("--contains", commitHash))
    val stdout = ExecOutputRedirect.ToString()
    try {
      ProcessExecutor(
        "git-local-branches",
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 8.minutes,
        args = arguments,
        stdoutRedirect = stdout,
        stderrRedirect = ExecOutputRedirect.ToStdOut("git-local-branches"),
      ).start()
    }
    catch (e: IllegalStateException) {
      // == false - safe check
      // Exception "no such commit" is not error. Just don't have this commit
      if (e.message?.contains("no such commit") == false) throw IllegalStateException(e)
    }
    return stdout.read().trim().split("\n").map { it.replace("\'", "") }
  }

  fun getRandomCommitInThePast(date: String, dir: Path): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-get-commits-on-specific-day",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "log", "--after=\\\"$date 00:00\\\"", "--before=\\\"$date 23:59\\\"", "--format=%h"),
      stdoutRedirect = stdout
    ).start()

    val commits = stdout.read().split("\n")
    return commits[Random().nextInt(commits.size)]
  }

  fun getRandomCommit(dir: Path, limit: Int, format: String = "--format=%h"): String {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-get-random-commit",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "log", "-n$limit", format),
      stdoutRedirect = stdout
    ).start()

    val commits = stdout.read().split("\n")
    return commits[Random().nextInt(commits.size)]
  }

  fun getLastCommit(dir: Path, targetBranch: String = "", format: String = "--format=%h"): String {
    val stdout = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "log")
    if (targetBranch.isNotEmpty()) arguments.addAll(listOf("-b", targetBranch))
    arguments.addAll(listOf("-n1", format))
    ProcessExecutor(
      "git-last-commit-get",
      workDir = dir,
      timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim()
  }

  fun createWorktree(dir: Path, targetBranch: String, worktree_dir: String, commit: String = ""): String {
    val stdout = ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "worktree", "add", "-f")
    val isBranchCreated = getLocalBranches(dir).contains(targetBranch)
    pruneWorktree(dir)
    when (isBranchCreated) {
      false -> {
        arguments.addAll(listOf("-b", targetBranch, worktree_dir))
        if (commit.isNotEmpty()) arguments.add(commit)
      }
      true -> {
        arguments.addAll(listOf(worktree_dir, targetBranch))
      }
    }
    ProcessExecutor(
      "git-create-worktree",
      workDir = dir,
      timeout = 20.minutes,
      args = arguments,
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    ).start()

    return stdout.read().trim()
  }

  /**
   * Sets local git config properties
   * Example git config --local user.email gitgod@git.git
   */
  fun setConfigProperty(dir: Path, propertyName: String, value: String) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git config",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "config", "--local", propertyName, "\"$value\""),
      stdoutRedirect = stdout
    ).start()
  }

  fun setConfigProperty(dir: Path, propertyName: String, value: Boolean) {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor(
      "git config",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "config", "--local", propertyName, "$value"),
      stdoutRedirect = stdout
    ).start()
  }

  fun buildDiff(dir: Path, file: File, outputFile: Path) {
    ProcessExecutor(
      "git-build-diff",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "diff", "--output=${outputFile.toFile().absolutePath}", file.absolutePath),
    ).start()
  }

  fun verifyBranch(branch: String): Boolean {
    val stdout = ExecOutputRedirect.ToString()
    runCatching {
      ProcessExecutor(
        "git verify branch",
        workDir = homeDir, timeout = 1.minutes,
        args = listOf("git", "show-ref", "refs/heads/$branch"),
        stdoutRedirect = stdout
      ).start()
    }
    return stdout.read().isNotEmpty()
  }

  fun countTotalNumberOfCommits(dir: Path): Int {
    val stdout = ExecOutputRedirect.ToString()

    ProcessExecutor(
      "git-revlist-all",
      workDir = dir.toAbsolutePath(),
      timeout = 2.minutes,
      args = listOf("git", "rev-list", "--count", "--all"),
      stdoutRedirect = stdout
    ).start()

    return stdout.read().trim().toInt()
  }

  /**
   * @return Duration in millis of command call
   */
  fun buildCommitGraph(dir: Path): Long {
    val start = System.currentTimeMillis()
    ProcessExecutor(
      "git-commit-graph",
      workDir = dir.toAbsolutePath(),
      timeout = 15.minutes,
      args = listOf("git", "commit-graph", "write", "--reachable", "--changed-paths"),
    ).start()
    return System.currentTimeMillis() - start
  }

}

