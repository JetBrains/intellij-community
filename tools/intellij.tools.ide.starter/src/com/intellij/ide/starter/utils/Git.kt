package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.EelProcessExecutor
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.Git.setConfigProperty
import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
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

    runGit(
      "git-local-branch-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
      stdoutRedirect = stdout
    )

    return stdout.read().trim()
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getLocalCurrentCommitHash(repositoryDirectory: Path): String {
    return getCommitHash(repositoryDirectory, "HEAD")
  }

  @Throws(IOException::class, InterruptedException::class)
  fun getCommitHash(repositoryDirectory: Path, branchName: String): String {
    require(branchName.isNotEmpty()) { "Branch name must not be empty" }
    val stdout = ExecOutputRedirect.ToString()

    runGit(
      "git-commit-get",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", branchName),
      stdoutRedirect = stdout
    )

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

  /** Marks a failure of [action] as a failure of the remote. */
  private fun <T> onRemote(command: String, action: () -> T): T =
    try {
      action()
    }
    catch (e: Exception) {
      throw GitRemoteException(command, cause = e)
    }

  /** The path as git sees it on the machine of the path. Eel does not map a path in an argument. */
  private fun Path.forGit(): String = toAbsolutePath().asEelPath().toString()

  /**
   * Runs git in [workDir] on the machine of [workDir].
   *
   * A local directory uses [ProcessExecutor] without starting an IntelliJ application.
   * A directory on a remote target uses [EelProcessExecutor].
   */
  private fun runGit(
    presentableName: String,
    workDir: Path,
    timeout: Duration = 1.minutes,
    args: List<String>,
    stdoutRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
    stderrRedirect: ExecOutputRedirect = ExecOutputRedirect.NoRedirect,
  ): Int = when (workDir.getEelDescriptor()) {
    LocalEelDescriptor -> ProcessExecutor(
      presentableName = presentableName,
      workDir = workDir,
      timeout = timeout,
      args = args,
      stdoutRedirect = stdoutRedirect,
      stderrRedirect = stderrRedirect,
      onlyEnrichExistedEnvVariables = true,
    ).start()
    else -> {
      val exitCode = EelProcessExecutor(
        presentableName = presentableName,
        workDir = workDir,
        timeout = timeout,
        args = args,
        stdoutRedirect = stdoutRedirect,
        stderrRedirect = stderrRedirect,
      ).start()
      check(exitCode == 0) {
        buildString {
          appendLine("External process `$presentableName` failed with code $exitCode")
          stderrRedirect.read().takeIf { it.isNotBlank() }?.let { appendLine(it) }
          stdoutRedirect.read().takeIf { it.isNotBlank() }?.let { appendLine(it) }
        }.trimEnd()
      }
      exitCode
    }
  }

  fun getRepoRoot(): Path {
    val stdout = ExecOutputRedirect.ToString()

    try {
      ProcessExecutor(
        "git-repo-root-get",
        // Asked in the IDE home directory rather than the process working directory. Under Bazel the latter
        // is the runfiles directory, from which `rev-parse` either fails outright or answers about the wrong
        // tree, and the checkout root it yields is then used for every output path the starter writes.
        workDir = homeDir,
        timeout = 1.minutes,
        args = listOf("git", "rev-parse", "--show-toplevel", "HEAD"),
        stdoutRedirect = stdout
      ).start()
    }
    catch (e: Exception) {
      // Outside the monorepo there may be no IDE home: `PathManager` throws then, and a failed `lazy` throws
      // again on every access, so the working directory stays the last resort.
      val fallbackDir = runCatching { homeDir }.getOrElse { Paths.get("").toAbsolutePath() }
      logError("There is a problem in detecting git repo root. Falling back to: '$fallbackDir'")
      return fallbackDir
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

    val command = arguments.joinToString(" ")

    // The retry drops each throwable, so keep the output for the message.
    val stdoutRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]")
    val stderrRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]")

    withRetryBlocking("Git clone $repoUrl failed", rollback = {
      logOutput("Deleting $destinationDir ...")

      withRetry("Failed to delete $destinationDir") { destinationDir.deleteRecursively() }
    }) {
      ProcessExecutor(
        presentableName = cmdName,
        workDir = destinationDir.parent.toAbsolutePath(),
        timeout = timeout,
        args = arguments,
        stdoutRedirect = stdoutRedirect,
        stderrRedirect = stderrRedirect,
        onlyEnrichExistedEnvVariables = true
      ).start()
    } ?: throw GitRemoteException(cmdName, buildString {
      appendLine("The command was: $command")
      if (isSshUrl(repoUrl)) appendLine(sshIdentityReport(repoUrl))
      appendLine("The last output of `$cmdName`:")
      appendLine(stderrRedirect.read())
      appendLine(stdoutRedirect.read())
    })
  }

  /** `true` when [repoUrl] uses SSH, either in the `ssh://` form or in the `git@host:path` form. */
  private fun isSshUrl(repoUrl: String): Boolean =
    repoUrl.startsWith("ssh://") || (!repoUrl.contains("://") && repoUrl.contains("@"))

  /**
   * The SSH identities that this machine offers to the host of [repoUrl].
   *
   * `Permission denied (publickey)` names no key, so the output of git alone does not say which identity
   * git sent. This report lists the public keys of the agent and the identity files of the configuration.
   * It prints no private key.
   */
  internal fun sshIdentityReport(repoUrl: String): String {
    val authority = repoUrl.substringAfter("://").substringBefore("/")
    val host = authority.substringAfter("@").substringBefore(":")

    // `GIT_SSH_COMMAND` overrides the agent and the identity files, so the report must use it too.
    // The split on a space keeps this simple. A quoted path in the variable breaks it, and that is
    // acceptable for a report.
    val sshCommand = System.getenv("GIT_SSH_COMMAND")?.takeIf { it.isNotBlank() }
    val ssh = sshCommand?.trim()?.split(" ") ?: listOf("ssh")

    val agentKeys = diagnosticOutput("ssh-add-list", listOf("ssh-add", "-L"))
    val identities = diagnosticOutput("ssh-config-get", ssh + listOf("-G", authority))
      .lines()
      .filter { it.startsWith("identityfile ") || it.startsWith("identityagent ") }
      .joinToString("\n")
      .ifEmpty { "(none)" }

    return buildString {
      appendLine("The SSH identities for '$host':")
      appendLine("  the ssh command of git: ${sshCommand ?: "(GIT_SSH_COMMAND is not set, so git runs `ssh`)"}")
      appendLine("  the identities that this command resolves (`${ssh.joinToString(" ")} -G $authority`):")
      appendLine(identities.prependIndent("    "))
      appendLine("  the public keys of the agent (`ssh-add -L`):")
      appendLine(agentKeys.prependIndent("    "))
    }
  }

  /** The output of [args], for a log message. A non-zero exit is fine, because this only reports. */
  private fun diagnosticOutput(name: String, args: List<String>): String {
    val stdout = ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToString()
    try {
      ProcessExecutor(
        presentableName = name,
        workDir = null,
        timeout = 30.seconds,
        args = args,
        stdoutRedirect = stdout,
        stderrRedirect = stderr,
        onlyEnrichExistedEnvVariables = true,
        analyzeProcessExit = false,
        silent = true,
      ).start()
    }
    catch (e: Exception) {
      return "the command `${args.joinToString(" ")}` failed: ${e.message}"
    }
    return (stdout.read() + stderr.read()).trim().ifEmpty { "(no output)" }
  }

  fun status(projectDir: Path): Int {
    val arguments = mutableListOf("git", "-c", "core.fsmonitor=false", "status")

    val startTimer = System.currentTimeMillis()

    val execOutStatus = ExecOutputRedirect.ToString()
    runGit(
      presentableName = "git-status",
      workDir = projectDir,
      timeout = 2.minutes,
      args = arguments,
      stdoutRedirect = execOutStatus,
      stderrRedirect = ExecOutputRedirect.ToString()
    )

    val endTimer = System.currentTimeMillis()
    val duration = (endTimer - startTimer).toInt()
    println("Git status took $duration")
    println("Git status output: ${execOutStatus.read()}")

    val execOutVersion = ExecOutputRedirect.ToString()
    runGit(
      presentableName = "git-version",
      workDir = projectDir,
      timeout = 1.minutes,
      args = listOf("git", "--version"),
      stdoutRedirect = execOutVersion,
      stderrRedirect = ExecOutputRedirect.ToString()
    )

    println("Git version: ${execOutVersion.read()}")

    val execOutConfig = ExecOutputRedirect.ToString()
    runGit(
      presentableName = "git-config",
      workDir = projectDir,
      timeout = 1.minutes,
      args = listOf("git", "config", "-l", "--show-scope"),
      stdoutRedirect = execOutConfig,
      stderrRedirect = ExecOutputRedirect.ToString()
    )

    println("Git config: ${execOutConfig.read()}")
    return duration
  }

  fun ensureRepository(repositoryDirectory: Path) {
    val cmdName = "git-rev-parse-show-prefix"
    val arguments = mutableListOf("git", "rev-parse", "--show-prefix")
    val stdoutRedirect = ExecOutputRedirect.ToString()
    val stderrRedirect = ExecOutputRedirect.ToStdOutAndString("[$cmdName]")
    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.seconds,
      args = arguments,
      stdoutRedirect = stdoutRedirect,
      stderrRedirect = stderrRedirect
    )
    if (stdoutRedirect.read().trim().isNotEmpty()) {
      error("Not a root of a git repository: $repositoryDirectory. $cmdName output: ${stdoutRedirect.read()}")
    }
  }

  fun reset(repositoryDirectory: Path, commitHash: String = "") {
    ensureRepository(repositoryDirectory)

    val cmdName = "git-reset"

    val arguments = mutableListOf("git", "reset", "--hard")
    if (commitHash.isNotEmpty()) arguments.add(commitHash)

    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  /**
   * Removes the untracked files. [includeIgnored] also removes the ignored files and the nested repositories.
   */
  fun clean(repositoryDirectory: Path, includeIgnored: Boolean = false) {
    val cmdName = "git-clean"

    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = if (includeIgnored) listOf("git", "clean", "-ffdx") else listOf("git", "clean", "-fd"),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }


  fun checkout(repositoryDirectory: Path, branchName: String, isForce: Boolean = false) {
    require(branchName.isNotEmpty()) { "Branch name should not be empty" }
    val cmdName = "git-checkout"

    val arguments = mutableListOf("git", "checkout")
    if (isForce) {
      arguments.add("-f")
    }
    arguments.add(branchName)

    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  fun checkoutNewBranch(repositoryDirectory: Path, branchName: String) {
    require(branchName.isNotEmpty()) { "Branch name should not be empty" }
    val cmdName = "git-checkout-new-branch"
    val stdout = ExecOutputRedirect.ToString()

    val arguments = mutableListOf("git", "checkout", "-b", branchName)

    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = arguments,
      stdoutRedirect = stdout,
      stderrRedirect = stdout
    )
  }

  fun createBranch(repositoryDirectory: Path, branchName: String, startPoint: String = "") {
    val cmdName = "git-create-branch"
    val stdout = ExecOutputRedirect.ToString()

    val arguments = mutableListOf("git", "branch", branchName)
    if (startPoint.isNotEmpty()) {
      arguments.add(startPoint)
    }
    runGit(
      cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout
    )
  }

  /**
   * Fetches from [remote]. A non-empty [ref] fetches only this reference, and a positive [depth] makes the fetch shallow.
   */
  fun fetch(repositoryDirectory: Path, remote: String = "", ref: String = "", depth: Int = 0, timeout: Duration = 10.minutes) {
    require(ref.isEmpty() || remote.isNotEmpty()) { "A reference to fetch needs a remote" }
    require(depth >= 0) { "Depth must not be negative" }
    val cmdName = "git-fetch"

    val arguments = mutableListOf("git", "fetch", "--no-auto-maintenance")
    if (depth > 0) arguments.add("--depth=$depth")
    if (remote.isNotEmpty()) arguments.add(remote)
    if (ref.isNotEmpty()) arguments.add(ref)

    onRemote(cmdName) {
      runGit(
        presentableName = cmdName,
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = timeout,
        args = arguments,
        stdoutRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]"),
        stderrRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]")
      )
    }
  }

  fun pull(repositoryDirectory: Path) {
    val cmdName = "git-pull"

    onRemote(cmdName) {
      runGit(
        presentableName = cmdName,
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 10.minutes,
        args = listOf("git", "-c", "maintenance.auto=false", "pull"),
        stdoutRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]"),
        stderrRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]")
      )
    }
  }

  fun rebase(repositoryDirectory: Path, newBase: String = "master") {
    val cmdName = "git-rebase"

    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = listOf("git", "rebase", newBase),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  fun merge(repositoryDirectory: Path, branchName: String, vararg options: String) {
    require(branchName.isNotEmpty()) { "Branch name should not be empty" }
    val cmdName = "git-merge"
    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 10.minutes,
      args = buildList {
        add("git")
        add("merge")
        addAll(options)
        add(branchName)
      },
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  fun push(repositoryDirectory: Path, branch: String = "master", remote: String = "origin", setUpstream: Boolean = false) {
    val cmdName = "git-push"

    val arguments = mutableListOf("git", "push")
    if (setUpstream) arguments.add("-u")
    arguments.addAll(listOf(remote, branch))

    onRemote(cmdName) {
      runGit(
        presentableName = cmdName,
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 10.minutes,
        args = arguments,
        stdoutRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]"),
        stderrRedirect = ExecOutputRedirect.ToStdOutAndTail("[$cmdName]")
      )
    }
  }

  /**
   * Points [ref] at [target], which creates [ref] if it does not exist yet.
   *
   * Use it for a reference that no plain branch command can write, for example a remote-tracking
   * reference such as `refs/remotes/origin/feature`.
   */
  fun updateRef(repositoryDirectory: Path, ref: String, target: String) {
    require(ref.isNotEmpty()) { "Reference should not be empty" }
    require(target.isNotEmpty()) { "Target should not be empty" }

    val cmdName = "git-update-ref"
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "update-ref", ref, target),
      stdoutRedirect = stdout,
      stderrRedirect = stdout
    )
  }

  fun deleteBranch(workDir: Path, targetBranch: String) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-delete-branch",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "branch", "-D", targetBranch),
      stdoutRedirect = stdout
    )
  }

  fun init(workDir: Path, bare: Boolean = false) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-init",
      workDir = workDir, timeout = 1.minutes,
      args = buildList {
        add("git")
        add("init")
        if (bare) add("--bare")
      },
      stdoutRedirect = stdout
    )
  }

  fun addAll(workDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-add-all",
      workDir = workDir, timeout = 1.minutes,
      args = listOf("git", "add", "*"),
      stdoutRedirect = stdout
    )
  }

  fun add(workDir: Path, filePath: String) {
    require(filePath.isNotEmpty()) { "File path should not be empty" }

    val cmdName = "git-add"
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      presentableName = cmdName,
      workDir = workDir,
      timeout = 1.minutes,
      args = buildList {
        add("git")
        add("add")
        add(filePath)
      },
      stdoutRedirect = stdout,
      stderrRedirect = stdout,
    )
  }

  fun rm(workDir: Path, filePath: String) {
    require(filePath.isNotEmpty()) { "File path should not be empty" }

    val cmdName = "git-rm"
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      presentableName = cmdName,
      workDir = workDir,
      timeout = 1.minutes,
      args = listOf("git", "rm", filePath),
      stdoutRedirect = stdout,
      stderrRedirect = stdout,
    )
  }

  fun mv(workDir: Path, from: String, to: String) {
    require(from.isNotEmpty() && to.isNotEmpty()) { "Paths should not be empty" }

    val cmdName = "git-mv"
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      presentableName = cmdName,
      workDir = workDir,
      timeout = 1.minutes,
      args = listOf("git", "mv", from, to),
      stdoutRedirect = stdout,
      stderrRedirect = stdout,
    )
  }

  fun commit(workDir: Path, message: String, author: String? = null): String {
    require(message.isNotEmpty()) { "Message should not be empty" }
    require(author == null || author.isNotBlank()) { "Author should not be blank" }

    val cmdName = "git-commit"
    val stdout = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "commit", "-m", message)
    if (author != null) arguments.add("--author=$author")

    runGit(
      presentableName = cmdName,
      workDir = workDir.toAbsolutePath(),
      timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout,
      stderrRedirect = stdout
    )

    return getLocalCurrentCommitHash(workDir)
  }

  fun move(workDir: Path, source: String, destination: String) {
    require(source.isNotEmpty() && destination.isNotEmpty()) { "Source and destination must not be empty" }

    val stdout = ExecOutputRedirect.ToString()
    runGit(
      presentableName = "git-mv",
      workDir = workDir.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "mv", source, destination),
      stdoutRedirect = stdout,
      stderrRedirect = stdout
    )
  }

  fun pruneWorktree(pathToDir: Path) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-prune-worktree",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "worktree", "prune"),
      stdoutRedirect = stdout
    )
  }

  /** Adds a worktree for the new branch [branchName] in [worktreePath]. The worktree must be on the machine of [workDir]. */
  fun worktreeAdd(workDir: Path, branchName: String, worktreePath: Path, startPoint: String = "") {
    val stdout = ExecOutputRedirect.ToString()

    val arguments = mutableListOf("git", "worktree", "add", "-b", branchName, worktreePath.forGit())
    if (startPoint.isNotEmpty()) {
      arguments.add(startPoint)
    }

    runGit(
      "git-worktree-add",
      workDir = workDir, timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout
    )
  }

  /** Removes the worktree in [worktreePath]. This is the path as git sees it, for example a path from [worktreeListPorcelain]. */
  fun worktreeRemove(workDir: Path, worktreePath: String) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-worktree-remove",
      workDir = workDir,
      timeout = 1.minutes,
      args = listOf("git", "worktree", "remove", "--force", worktreePath),
      stdoutRedirect = stdout
    )
  }

  fun worktreeListPorcelain(workDir: Path): String? {
    val stdout = ExecOutputRedirect.ToString()
    return try {
      runGit(
        "git-worktree-list",
        workDir = workDir,
        timeout = 1.minutes,
        args = listOf("git", "worktree", "list", "--porcelain"),
        stdoutRedirect = stdout
      )
      stdout.read()
    }
    catch (_: Exception) {
      null
    }
  }

  fun getStatus(pathToDir: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-status",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "status"),
      stdoutRedirect = stdout
    )
    return stdout.read()
  }

  fun getStagedFiles(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-diff-cached-name-only",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "diff", "--cached", "--name-only"),
      stdoutRedirect = stdout
    )
    return stdout.read().trim()
  }

  fun getUnstagedFiles(repositoryDirectory: Path): String {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-diff-name-only",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "diff", "--name-only"),
      stdoutRedirect = stdout
    )
    return stdout.read().trim()
  }

  fun getDiff(pathToDir: Path, diffStart: String, diffEnd: String): String {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-diff",
      workDir = pathToDir, timeout = 1.minutes,
      args = listOf("git", "diff", diffStart, diffEnd, "--unified=0", "--no-renames"),
      stdoutRedirect = stdout
    )
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
      runGit(
        "git-local-branches",
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 8.minutes,
        args = arguments,
        stdoutRedirect = stdout,
        stderrRedirect = ExecOutputRedirect.ToStdOut("git-local-branches"),
      )
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

    runGit(
      "git-get-commits-on-specific-day",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "log", "--after=\\\"$date 00:00\\\"", "--before=\\\"$date 23:59\\\"", "--format=%h"),
      stdoutRedirect = stdout
    )

    val commits = stdout.read().split("\n")
    return commits[Random().nextInt(commits.size)]
  }

  fun getRandomCommit(dir: Path, limit: Int, format: String = "--format=%h"): String {
    val stdout = ExecOutputRedirect.ToString()

    runGit(
      "git-get-random-commit",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "log", "-n$limit", format),
      stdoutRedirect = stdout
    )

    val commits = stdout.read().split("\n")
    return commits[Random().nextInt(commits.size)]
  }

  fun getLastCommit(dir: Path, targetBranch: String = "", format: String = "--format=%h"): String {
    val stdout = ExecOutputRedirect.ToString()
    val arguments = mutableListOf("git", "log")
    if (targetBranch.isNotEmpty()) arguments.addAll(listOf("-b", targetBranch))
    arguments.addAll(listOf("-n1", format))
    runGit(
      "git-last-commit-get",
      workDir = dir,
      timeout = 1.minutes,
      args = arguments,
      stdoutRedirect = stdout
    )

    return stdout.read().trim()
  }

  /**
   * Returns the parent commit hashes of [ref] (one per parent), via `git rev-parse <ref>^@`.
   * A root commit yields an empty list.
   */
  fun getParentCommits(repositoryDirectory: Path, ref: String = "HEAD"): List<String> {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-rev-parse-parents",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "rev-parse", "$ref^@"),
      stdoutRedirect = stdout
    )
    return stdout.read().trim().lines().filter { it.isNotBlank() }
  }

  fun getLog(repositoryDirectory: Path, count: Int): List<String> {
    require(count > 0) { "Count must be positive" }
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-log-oneline",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "log", "--oneline", "-n$count"),
      stdoutRedirect = stdout
    )
    return stdout.read().trim().lines().filter { it.isNotBlank() }
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
    runGit(
      "git-create-worktree",
      workDir = dir,
      timeout = 20.minutes,
      args = arguments,
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    )

    return stdout.read().trim()
  }

  /**
   * Sets local git config properties
   * Example git config --local user.email gitgod@git.git
   */
  fun setConfigProperty(dir: Path, propertyName: String, value: String) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git config",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "config", "--local", propertyName, "\"$value\""),
      stdoutRedirect = stdout
    )
  }

  fun setConfigProperty(dir: Path, propertyName: String, value: Boolean) {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git config",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "config", "--local", propertyName, "$value"),
      stdoutRedirect = stdout
    )
  }

  fun getConfigProperty(repositoryDirectory: Path, propertyName: String): String {
    require(propertyName.isNotEmpty()) { "Property name must not be empty" }
    val propertyValue = ExecOutputRedirect.ToString()

    runCatching {
      runGit(
        propertyName,
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 10.seconds,
        args = listOf("git", "config", propertyName),
        stdoutRedirect = propertyValue,
        stderrRedirect = ExecOutputRedirect.ToString()
      )
    }

    return propertyValue.read().trim()
  }

  /**
   * Returns the URL configured for [remote], or an empty string if the remote is not configured.
   */
  fun getRemoteUrl(repositoryDirectory: Path, remote: String): String {
    val stdout = ExecOutputRedirect.ToString()
    runCatching {
      runGit(
        "git-remote-get-url",
        workDir = repositoryDirectory.toAbsolutePath(),
        timeout = 10.seconds,
        args = listOf("git", "remote", "get-url", remote),
        stdoutRedirect = stdout,
        stderrRedirect = ExecOutputRedirect.ToString()
      )
    }
    return stdout.read().trim()
  }

  fun addRemote(repositoryDirectory: Path, remote: String, url: String) {
    val cmdName = "git-remote-add"
    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "remote", "add", remote, url),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  fun setRemoteUrl(repositoryDirectory: Path, remote: String, url: String) {
    val cmdName = "git-remote-set-url"
    runGit(
      presentableName = cmdName,
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "remote", "set-url", remote, url),
      stdoutRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]"),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[$cmdName]")
    )
  }

  fun buildDiff(dir: Path, file: Path, outputFile: Path) {
    runGit(
      "git-build-diff",
      workDir = dir, timeout = 1.minutes,
      args = listOf("git", "diff", "--output=${outputFile.forGit()}", file.forGit()),
    )
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

    runGit(
      "git-revlist-all",
      workDir = dir.toAbsolutePath(),
      timeout = 2.minutes,
      args = listOf("git", "rev-list", "--count", "--all"),
      stdoutRedirect = stdout
    )

    return stdout.read().trim().toInt()
  }

  /**
   * @return Duration in millis of command call
   */
  fun buildCommitGraph(dir: Path): Long {
    val start = System.currentTimeMillis()
    runGit(
      "git-commit-graph",
      workDir = dir.toAbsolutePath(),
      timeout = 15.minutes,
      args = listOf("git", "commit-graph", "write", "--reachable", "--changed-paths"),
    )
    return System.currentTimeMillis() - start
  }

  /**
   * Returns a list of tracked files modified since the last commit (both staged and unstaged).
   * Untracked files (e.g. qodana.yaml added by the test framework) are not included.
   */
  @Throws(IOException::class, InterruptedException::class)
  fun getModifiedTrackedFiles(repositoryDirectory: Path): List<String> {
    val stdout = ExecOutputRedirect.ToString()
    runGit(
      "git-diff-name-only",
      workDir = repositoryDirectory.toAbsolutePath(),
      timeout = 1.minutes,
      args = listOf("git", "diff", "--name-only", "HEAD"),
      stdoutRedirect = stdout,
      stderrRedirect = ExecOutputRedirect.ToStdOut("[git-diff-name-only]")
    )
    return stdout.read().trim().lines().filter { it.isNotBlank() }
  }
}

/**
 * Sets the URL of [remote] to [url], if the repository has that remote and its URL differs.
 *
 * A cached checkout keeps the URL of the clone that created it. Without this, a change of the URL of a test
 * project never reaches an agent that already holds the checkout.
 *
 * @return `true` if the URL changed
 */
fun Git.setRemoteUrlIfDifferent(repositoryDirectory: Path, url: String, remote: String = "origin"): Boolean {
  require(url.isNotEmpty()) { "URL must not be empty" }

  val currentUrl = getRemoteUrl(repositoryDirectory, remote)
  if (currentUrl.isEmpty() || currentUrl == url) return false

  logOutput("The $remote of $repositoryDirectory is '$currentUrl'. Setting it to '$url' ...")
  setRemoteUrl(repositoryDirectory, remote, url)
  return true
}

/**
 * Configures git user.name and user.email for the repository if not already set.
 * Configuration is applied locally (repository-level, not global).
 *
 * @param repositoryDirectory path to the git repository
 * @param userName the user name to set if not configured (default: "Test User")
 * @param userEmail the user email to set if not configured (default: "test-user@jetbrains.com")
 */
fun Git.configureGitUserIfNotSet(
  repositoryDirectory: Path,
  userName: String = "Test User",
  userEmail: String = "test-user@jetbrains.com",
) {
  val currentUserName = getConfigProperty(repositoryDirectory, "user.name")
  val currentUserEmail = getConfigProperty(repositoryDirectory, "user.email")

  setPropertyIfEmpty(repositoryDirectory, "user.name", currentUserName, userName)
  setPropertyIfEmpty(repositoryDirectory, "user.email", currentUserEmail, userEmail)
}

private fun setPropertyIfEmpty(repositoryDirectory: Path, propertyName: String, currentValue: String, defaultValue: String) {
  if (currentValue.isEmpty()) {
    require(defaultValue.isNotEmpty()) { "defaultValue must not be empty" }
    setConfigProperty(repositoryDirectory, propertyName, defaultValue)
    logOutput("Git $propertyName set to '$defaultValue' for repository: $repositoryDirectory")
  }
}
