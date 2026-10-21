package com.intellij.ide.starter.project

import com.intellij.ide.starter.ide.IDETestContext
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object GitHubProject {
  /**
   * Git clones the repo over HTTPS, so a machine needs no SSH key. A machine that holds an SSH key for
   * github.com must use [fromGithubOverSsh], because HTTPS shares the rate limit of github.com.
   *
   * [repoRelativeUrl] - Relative path to GitHub repo. Eg: "alexholmes/avro-maven"
   * [projectDirRelativePath] - Relative path from repo root. Eg: { it.resolve("path/to/deeply/hidden/project") }
   */
  fun fromGithub(
    branchName: String = "main",
    commitHash: String = "",
    repoRelativeUrl: String,
    downloadTimeout: Duration = 10.minutes,
    description: String = "",
    isReusable: Boolean = false,
    configureProjectBeforeUse: (IDETestContext) -> Unit = {}
  ): GitProjectInfo = create(
    baseUrl = Const.GITHUB_HTTP_BASE_URL,
    branchName = branchName,
    commitHash = commitHash,
    repoRelativeUrl = repoRelativeUrl,
    downloadTimeout = downloadTimeout,
    description = description,
    isReusable = isReusable,
    configureProjectBeforeUse = configureProjectBeforeUse
  )

  /**
   * The same as [fromGithub], but Git clones the repo over SSH.
   *
   * An HTTPS URL shares the rate limit of github.com. An SSH URL does not.
   * The machine must hold an SSH key for github.com.
   *
   * [repoRelativeUrl] - Relative path to GitHub repo. Eg: "alexholmes/avro-maven"
   */
  fun fromGithubOverSsh(
    branchName: String = "main",
    commitHash: String = "",
    repoRelativeUrl: String,
    downloadTimeout: Duration = 10.minutes,
    description: String = "",
    isReusable: Boolean = false,
    configureProjectBeforeUse: (IDETestContext) -> Unit = {}
  ): GitProjectInfo = create(
    baseUrl = Const.GITHUB_SSH_BASE_URL,
    branchName = branchName,
    commitHash = commitHash,
    repoRelativeUrl = repoRelativeUrl,
    downloadTimeout = downloadTimeout,
    description = description,
    isReusable = isReusable,
    configureProjectBeforeUse = configureProjectBeforeUse
  )

  private fun create(
    baseUrl: String,
    branchName: String,
    commitHash: String,
    repoRelativeUrl: String,
    downloadTimeout: Duration,
    description: String,
    isReusable: Boolean,
    configureProjectBeforeUse: (IDETestContext) -> Unit
  ): GitProjectInfo {
    val repoRelativeUrlWithGitSuffix = if (repoRelativeUrl.endsWith(".git")) repoRelativeUrl
    else "$repoRelativeUrl.git"

    return GitProjectInfo(
      branchName = branchName,
      commitHash = commitHash,
      repositoryUrl = URI(baseUrl).resolve(repoRelativeUrlWithGitSuffix).toString(),
      downloadTimeout = downloadTimeout,
      description = description,
      isReusable = isReusable,
      configureProjectBeforeUse = configureProjectBeforeUse
    )
  }
}