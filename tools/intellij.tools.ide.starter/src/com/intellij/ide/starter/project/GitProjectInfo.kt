package com.intellij.ide.starter.project

import com.intellij.execution.wsl.EelDescriptorWithWslDistribution
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.abortOnUnavailableGitRemote
import com.intellij.ide.starter.utils.setRemoteUrlIfDifferent
import com.intellij.ide.starter.utils.withRetryBlocking
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import org.kodein.di.instance
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
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

  /**
   * The checkout that the test uses, on the machine of the target.
   *
   * On the local target, it is [hostRepositoryRootDir]. A Docker container sees [hostRepositoryRootDir] here through a bind mount.
   * A WSL guest has its own checkout here, which fetches from [hostRepositoryRootDir].
   */
  val repositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()
      return globalPaths.cacheDirForProjects.resolve("unpacked").createDirectories().resolve(repositoryName)
    }

  /** The checkout on the host. The credentials for [repositoryUrl] exist only on the host. */
  val hostRepositoryRootDir: Path
    get() {
      val globalPaths by di.instance<GlobalPaths>()
      return globalPaths.localCacheDirectory.resolve("projects").resolve("unpacked").createDirectories().resolve(repositoryName)
    }

  private val repositoryName: String
    get() = repositoryUrl.split("/").last().split(".git").first()

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
    Git.setRemoteUrlIfDifferent(repoRoot, repositoryUrl)

    if (isReusable) {
      resetReusableCheckoutOnFirstUse(repoRoot)
    }
    else {
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
    val hostRoot = provisionHostRepository()
    val targetRoot = repositoryRootDir
    if (isInWslGuest(targetRoot)) {
      checkoutInTarget(hostRoot, targetRoot, cleanWorkingTree = !isReusable)
      if (isReusable) {
        resetReusableCheckoutOnFirstUse(targetRoot)
      }
    }
    check(isCheckout(targetRoot)) {
      "The project directory $targetRoot has no checkout of $repositoryUrl. Its environment is ${targetRoot.getEelDescriptor()}. " +
      "The directory must be the host checkout $hostRoot, a bind mount of it, or a checkout in a WSL guest."
    }
    return targetRoot.let(projectHomeRelativePath)
  }

  /**
   * Checks out the commit of this project in [targetDir], on the machine of [targetDir]. The checkout has no local changes.
   * Git on this machine must be able to read the host checkout, so the machine is the host or a WSL guest.
   */
  internal fun checkoutInto(targetDir: Path) {
    checkoutInTarget(provisionHostRepository(), targetDir, cleanWorkingTree = true)
  }

  private fun isInWslGuest(dir: Path): Boolean = dir.getEelDescriptor() is EelDescriptorWithWslDistribution

  /** Moves the host checkout to [branchName] and [commitHash]. A failure tries once more from a new clone. */
  private fun provisionHostRepository(): Path {
    val hostRoot = hostRepositoryRootDir
    try {
      if (isCheckout(hostRoot)) processReusable(hostRoot) else cleanupAndCloneRepo(hostRoot)
      setupRepositoryState(hostRoot)
    }
    catch (failure: Exception) {
      logError("Failed to setup the test project git repository state as: $this. " +
               "Trying one more time from clean checkout.", failure)

      try {
        cleanupAndCloneRepo(hostRoot)
        setupRepositoryState(hostRoot)
      }
      catch (retryFailure: Exception) {
        retryFailure.addSuppressed(failure)
        abortOnUnavailableGitRemote(repositoryUrl, retryFailure)
        throw retryFailure
      }
    }
    return hostRoot
  }

  /** Checks out the commit of [hostRoot] in [targetRoot]. */
  private fun checkoutInTarget(hostRoot: Path, targetRoot: Path, cleanWorkingTree: Boolean) {
    check(!withSubmodules) {
      "The checkout in $targetRoot cannot get the submodules of $repositoryUrl, because it fetches only from the host checkout"
    }
    GitTargetCheckout.checkoutHostHead(hostRepositoryDir = hostRoot,
                                       targetDir = targetRoot,
                                       cleanWorkingTree = cleanWorkingTree,
                                       timeout = downloadTimeout)
  }

  fun onCommit(commitHash: String): GitProjectInfo = copy(commitHash = commitHash)

  fun onBranch(branchName: String): GitProjectInfo = copy(branchName = branchName)

  override fun getDescription(): String {
    return description
  }

  /** The name of the project directory. It holds the sub-folder of [projectHomeRelativePath], so it needs no clone. */
  override fun getName(): String = projectPath.fileName.toString()
}

/** The checkouts of a reusable project that this process has reset. */
private val resetReusableCheckouts: MutableSet<Path> = ConcurrentHashMap.newKeySet()

/**
 * Resets the tracked files of the checkout in [repoRoot] and removes its untracked and ignored files, once for each checkout
 * in the process.
 *
 * A test can leave its edits in a reusable checkout, and the next test process would take them as the clean project.
 * Later calls in the same process keep the checkout as it is.
 */
internal fun resetReusableCheckoutOnFirstUse(repoRoot: Path) {
  if (!resetReusableCheckouts.add(repoRoot.toAbsolutePath().normalize())) {
    return
  }
  Git.reset(repositoryDirectory = repoRoot)
  Git.clean(repoRoot, includeIgnored = true)
}
