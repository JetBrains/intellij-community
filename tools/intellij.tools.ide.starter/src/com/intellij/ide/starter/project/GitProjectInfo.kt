package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.abortOnUnavailableGitRemote
import com.intellij.ide.starter.utils.withRetryBlocking
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Project, hosted as a Git repository
 */
data class GitProjectInfo(
  /**
   * SSH or HTTPS url.
   * Bear in mind, that it should be either available without authentication or you should have an ssh keys on the machine.
   * HTTPS is a subject to rate limit.
   */
  val repositoryUrl: String,

  /** If empty - latest */
  val commitHash: String = "",

  /**
   * Branch to check out. If empty, the remote default branch (HEAD) is used.
   * When switching between branches in tests, specify this explicitly to avoid running on an unexpected branch.
   */
  val branchName: String = "",

  override val isReusable: Boolean = false,
  override val downloadTimeout: Duration = 10.minutes,

  /**
   * Set to true if you test don't need full VCS history and branches and project doesn't use submodules.
   */
  val shallow: Boolean = false,

  /**
   * Set to true if you want to include submodules. The option: `--recurse-submodules` will be added.
   */
  val withSubmodules: Boolean = false,

  /**
   * Relative path inside Image file, where project home is located
   */
  val projectHomeRelativePath: (Path) -> Path = { it },
  override val configureProjectBeforeUse: (IDETestContext) -> Unit = {},
  private val description: String = "",
) : ProjectInfoSpec {

  val repositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()

      // TODO: https://youtrack.jetbrains.com/issue/AT-2013/Eel-in-Starter-Make-GitProjectInfo-and-Git-aware-of-target-eel
      // as of now just use local cache directory for git project
      val projectsUnpacked = globalPaths.localCacheDirectory.resolve("projects").resolve("unpacked").createDirectories()

      return projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())
    }

  // TODO: Remove this after https://youtrack.jetbrains.com/issue/AT-2013/Eel-in-Starter-Make-GitProjectInfo-and-Git-aware-of-target-eel
  // after setting up project we should return "real" directory path (not local) to use in remote target (Docker, WSL, etc)
  private val remoteRepositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()
      val projectsUnpacked = globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories()
      return projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())
    }

  val projectPath: Path
    get() = repositoryRootDir.let(projectHomeRelativePath)

  @OptIn(ExperimentalPathApi::class)
  private fun cleanupAndCloneRepo(repoRoot: Path) {
    logOutput("Deleting $repoRoot and cloning $repositoryUrl ...")
    // `deleteRecursively` does nothing on an absent path.
    withRetryBlocking("Failed to delete $repoRoot", retries = 3,
                      rollback = { execHardRemove(repoRoot) }) { repoRoot.deleteRecursively() }

    Git.clone(repoUrl = repositoryUrl,
              destinationDir = repoRoot,
              branchName = branchName,
              shallow = shallow,
              withSubmodules = withSubmodules,
              timeout = downloadTimeout)
  }

  /** Moves the checkout in [repoRoot] to [branchName] and [commitHash]. */
  private fun setupRepositoryState(repoRoot: Path) {
    if (!isReusable) {
      Git.reset(repositoryDirectory = repoRoot)
      Git.clean(repoRoot)
    }
    val localBranch = Git.getLocalGitBranch(repoRoot)
    if (branchName.isNotEmpty() && localBranch != branchName) {
      Git.checkout(repositoryDirectory = repoRoot, branchName = branchName)
    }

    if (commitHash.isNotEmpty() && Git.getLocalCurrentCommitHash(repoRoot) != commitHash) {
      // When branchName is empty, getLocalBranches returns [""] on empty output, so contains("") would
      // be a false positive. Guard with branchName.isNotEmpty() so we always pull when branch is unknown.
      val hasCommit = branchName.isNotEmpty() && Git.getLocalBranches(repoRoot, commitHash).contains(branchName)
      if (!hasCommit) Git.pull(repoRoot)
      Git.reset(repositoryDirectory = repoRoot, commitHash = commitHash)
    }
    else if (commitHash.isEmpty() && branchName.isNotEmpty()) {
      Git.fetch(repoRoot)
      Git.reset(repositoryDirectory = repoRoot, commitHash = "origin/$branchName")
    }
    else if (commitHash.isEmpty() && branchName.isEmpty()) {
      Git.fetch(repoRoot)
      Git.reset(repositoryDirectory = repoRoot, commitHash = "FETCH_HEAD")
    }
  }

  /** [repoRoot] holds a git checkout of the test project. A corrupted checkout counts as no checkout. */
  private fun isCheckout(repoRoot: Path) = repoRoot.exists() && repoRoot.listDirectoryEntries(".git").isNotEmpty()

  private fun execHardRemove(dir: Path) {
    logOutput("Trying to remove the directory $dir with OS command line ...")
    val workDir = dir.parent

    if (OS.CURRENT == OS.Windows) {
      ProcessExecutor(
        "rmdir",
        workDir = workDir,
        timeout = 1.minutes,
        args = listOf("cmd.exe", "/c", "rmdir", "/s", "/q", dir.absolutePathString()),
        stdoutRedirect = ExecOutputRedirect.ToStdOut("rmdir"),
        stderrRedirect = ExecOutputRedirect.ToStdOut("rmdir")
      ).start()
    }
    else {
      ProcessExecutor(
        "rm",
        workDir = workDir,
        timeout = 1.minutes,
        args = listOf("rm", "-rf", dir.absolutePathString()),
        stdoutRedirect = ExecOutputRedirect.ToStdOut("rm"),
        stderrRedirect = ExecOutputRedirect.ToStdOut("rm")
      ).start()
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun processReusable(repoRoot: Path) {
    if (isReusable) return

    //keeping `.git`, so [setupRepositoryState] restores the files without a download.
    repoRoot.listDirectoryEntries().filterNot { it.endsWith(".git") }.forEach { entry ->
      try {
        entry.deleteRecursively()
      }
      catch (e: Exception) {
        logError("Failed to delete $entry. Trying to remove it with an OS command.", e)
        execHardRemove(entry)
      }
    }
  }

  override fun downloadAndUnpackProject(): Path {
    try {
      if (isCheckout(repositoryRootDir)) processReusable(repositoryRootDir) else cleanupAndCloneRepo(repositoryRootDir)
      setupRepositoryState(repositoryRootDir)
    }
    catch (failure: Exception) {
      logError("Failed to setup the test project git repository state as: $this. " +
               "Trying one more time from clean checkout.", failure)

      try {
        cleanupAndCloneRepo(repositoryRootDir)
        setupRepositoryState(repositoryRootDir)
      }
      catch (retryFailure: Exception) {
        retryFailure.addSuppressed(failure)
        abortOnUnavailableGitRemote(repositoryUrl, retryFailure)
        throw retryFailure
      }
    }

    return remoteRepositoryRootDir.let(projectHomeRelativePath)
  }

  fun onCommit(commitHash: String): GitProjectInfo = copy(commitHash = commitHash)

  fun onBranch(branchName: String): GitProjectInfo = copy(branchName = branchName)

  override fun getDescription(): String {
    return description
  }
}