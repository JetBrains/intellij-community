package com.intellij.ide.starter.project

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.wsl.EelDescriptorWithWslDistribution
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.GitRemoteException
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.time.Duration

/**
 * Checks out the `HEAD` of a host repository into a directory on the machine of a target.
 *
 * Git runs on the machine of the target directory through Eel. It fetches from the host repository over the path that this machine sees.
 * So the fetch needs no credential and no network access.
 */
internal object GitTargetCheckout {
  /**
   * Moves the checkout in [targetDir] to the commit of `HEAD` in [hostRepositoryDir].
   *
   * A fetch runs only when [targetDir] is at a different commit. It takes only this commit.
   * When [cleanWorkingTree] is set, the checkout loses all local changes and all untracked and ignored files.
   *
   * The checkout is shallow, it has a detached `HEAD`, and it has no remote.
   */
  fun checkoutHostHead(hostRepositoryDir: Path, targetDir: Path, cleanWorkingTree: Boolean, timeout: Duration) {
    val commit = Git.getLocalCurrentCommitHash(hostRepositoryDir)
    val remoteUrl = pathOnMachineOf(hostRepositoryDir, targetDir.getEelDescriptor())
    logOutput("Checking out $commit of $hostRepositoryDir into $targetDir from '$remoteUrl' ...")

    val currentCommit = if (isCheckout(targetDir)) {
      currentCommitOrNull(targetDir)
    }
    else {
      targetDir.createDirectories()
      Git.init(targetDir)
      null
    }

    if (currentCommit != commit) {
      try {
        Git.fetch(targetDir, remote = remoteUrl, ref = "HEAD", depth = 1, timeout = timeout)
      }
      catch (e: GitRemoteException) {
        // The host checkout is a part of the test setup, not a remote. So this failure must fail the test, and not skip it.
        throw IllegalStateException("Failed to fetch $commit from the host checkout '$remoteUrl' into $targetDir", e)
      }
      Git.checkout(targetDir, commit, isForce = true)
    }
    else {
      logOutput("The checkout in $targetDir is already at $commit. No fetch is necessary")
    }

    if (cleanWorkingTree) {
      Git.reset(targetDir)
      Git.clean(targetDir, includeIgnored = true)
    }
  }

  /**
   * The path of the host directory [hostDir] as git on the machine of [descriptor] sees it.
   *
   * A directory on the same machine keeps its path. A WSL guest sees a Windows directory under its mount root, for example `/mnt/c`.
   */
  fun pathOnMachineOf(hostDir: Path, descriptor: EelDescriptor): String {
    val hostPath = hostDir.toAbsolutePath()
    if (hostPath.getEelDescriptor().equalsMayBeUsingMachine(descriptor)) return hostPath.asEelPath().toString()

    val distribution = (descriptor as? EelDescriptorWithWslDistribution)?.distribution
                       ?: error("Git on $descriptor cannot read the host directory $hostPath. Only the host and a WSL guest can read it")
    return distribution.getWslPath(hostPath)
           ?: error("The WSL distribution ${distribution.id} has no path for the host directory $hostPath")
  }

  private fun isCheckout(dir: Path): Boolean = dir.resolve(".git").exists()

  /** The commit of `HEAD` in [dir], or `null` if the repository has no commit yet. */
  private fun currentCommitOrNull(dir: Path): String? =
    try {
      Git.getLocalCurrentCommitHash(dir)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      logOutput("The checkout in $dir has no commit yet: ${e.message}")
      null
    }
}
