@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.vcs

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.index.GitFileStatus
import git4idea.index.getStatus
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class VcsToolset : McpToolset {

  @Serializable
  class VcsRoots(val roots: Array<VcsRoot>)

  @Serializable
  class VcsRoot(
    @property:McpDescription("Path of repository relative to the project directory. Empty string means the project root")
    val pathRelativeToProject: String,
    @property:McpDescription("VCS used by this repository")
    val vcsName: String)

  @Serializable
  data class GitStatusResult(
    val repositories: List<RepositoryGitStatus>
  )

  @Serializable
  data class RepositoryGitStatus(
    @property:McpDescription("Path of this repository relative to project root. Empty string means the project root")
    val repositoryPathRelativeToProject: String,
    @property:McpDescription("Current branch name, or null when detached")
    val currentBranch: String?,
    @property:McpDescription("True when repository has no status entries matching the selected filters")
    val isClean: Boolean,
    @property:McpDescription("Total number of status entries before applying 'limit'")
    val totalEntries: Int,
    @property:McpDescription("True when entries were truncated by 'limit'")
    val hasMoreEntries: Boolean,
    @property:McpDescription("Number of entries with staged changes")
    val stagedCount: Int,
    @property:McpDescription("Number of entries with unstaged changes")
    val unstagedCount: Int,
    @property:McpDescription("Number of untracked entries")
    val untrackedCount: Int,
    @property:McpDescription("Number of ignored entries")
    val ignoredCount: Int,
    @property:McpDescription("Number of conflicted entries")
    val conflictedCount: Int,
    val entries: List<GitStatusEntry>
  )

  @Serializable
  data class GitStatusEntry(
    @property:McpDescription("Path relative to repository root")
    val pathRelativeToRepository: String,
    @property:McpDescription("Index status code from git status porcelain output")
    val indexStatus: String,
    @property:McpDescription("Working tree status code from git status porcelain output")
    val workTreeStatus: String,
    @property:McpDescription("Original path for renames/copies, relative to repository root")
    val originalPathRelativeToRepository: String? = null
  )

  private data class ResolvedGitRoot(
    val root: VirtualFile,
    val repositoryPathRelativeToProject: String,
    val currentBranch: String?,
  )

  @McpTool
  @McpDescription("""Retrieves the list of VCS roots in the project.
    |This is useful to detect all repositories in a multi-repository project.""")
  suspend fun get_repositories(): VcsRoots {
    val project = currentCoroutineContext().project
    val projectDirectory = project.projectDirectory
    val vcs = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()
      .map { VcsRoot(projectDirectory.relativizeIfPossible(it.path), it.vcs?.name ?: "<Unknown VCS>") }
    return VcsRoots(vcs.toTypedArray())
  }

  @McpTool
  @McpDescription("""
    |Retrieves Git status for one or more repositories in the current project.
    |Returns porcelain-style index/worktree status codes and summary counters.
    |By default all Git repositories are returned.
  """)
  suspend fun git_status(
    @McpDescription("Optional path relative to project root used to select a single containing repository")
    repositoryPathRelativeToProject: String? = null,
    @McpDescription("Whether to include untracked files")
    includeUntracked: Boolean = true,
    @McpDescription("Whether to include ignored files")
    includeIgnored: Boolean = false,
    @McpDescription("Maximum number of entries returned per repository")
    limit: Int = 1000,
  ): GitStatusResult {
    if (limit <= 0) {
      mcpFail("limit must be > 0")
    }

    val project = currentCoroutineContext().project
    val repositoryManager = GitRepositoryManager.getInstance(project)
    val repositories = resolveRepositories(project, repositoryManager, repositoryPathRelativeToProject)
    val roots = if (repositories.isNotEmpty()) {
      val projectDirectory = project.projectDirectory
      repositories.map { repository ->
        ResolvedGitRoot(
          root = repository.root,
          repositoryPathRelativeToProject = projectDirectory.relativizeIfPossible(repository.root),
          currentBranch = repository.currentBranchName,
        )
      }.sortedBy { it.repositoryPathRelativeToProject }
    }
    else {
      val fallbackRoot = resolveFallbackRepositoryRoot(project, repositoryPathRelativeToProject)
      when {
        fallbackRoot != null -> listOf(fallbackRoot)
        repositoryPathRelativeToProject != null -> mcpFail("No git repository found for path: $repositoryPathRelativeToProject")
        else -> emptyList()
      }
    }

    val statuses = roots.map { root ->
      try {
        toRepositoryStatus(project, root, includeUntracked, includeIgnored, limit)
      }
      catch (e: VcsException) {
        mcpFail("Failed to get git status for repository '${root.repositoryPathRelativeToProject}': ${e.message}")
      }
    }

    return GitStatusResult(statuses)
  }

  private fun resolveRepositories(
    project: Project,
    repositoryManager: GitRepositoryManager,
    repositoryPathRelativeToProject: String?,
  ): List<GitRepository> {
    val repositories = repositoryManager.repositories
    if (repositoryPathRelativeToProject == null) {
      return repositories
    }

    val resolvedPath = project.resolveInProject(repositoryPathRelativeToProject)
    val matching = repositories.filter { repository ->
      resolvedPath.startsWith(repositoryRootPath(repository))
    }
    return if (matching.isEmpty()) emptyList() else listOf(matching.maxBy { repositoryRootPath(it).nameCount })
  }

  private fun resolveFallbackRepositoryRoot(project: Project, repositoryPathRelativeToProject: String?): ResolvedGitRoot? {
    val projectDirectory = project.projectDirectory
    val targetPath = repositoryPathRelativeToProject?.let(project::resolveInProject) ?: projectDirectory
    var current = when {
      targetPath.exists() && targetPath.isDirectory() -> targetPath
      else -> targetPath.parent ?: targetPath
    }

    while (current.startsWith(projectDirectory)) {
      if (current.resolve(".git").exists()) {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(current) ?: break
        val currentBranch = try {
          readCurrentBranchName(project, root)
        }
        catch (_: VcsException) {
          null
        }
        return ResolvedGitRoot(
          root = root,
          repositoryPathRelativeToProject = projectDirectory.relativizeIfPossible(root),
          currentBranch = currentBranch,
        )
      }

      val parent = current.parent ?: break
      current = parent
    }

    return null
  }

  @Throws(VcsException::class)
  private fun toRepositoryStatus(
    project: Project,
    repository: ResolvedGitRoot,
    includeUntracked: Boolean,
    includeIgnored: Boolean,
    limit: Int,
  ): RepositoryGitStatus {
    val statuses = getStatus(
      project = project,
      root = repository.root,
      withRenames = true,
      withUntracked = includeUntracked,
      withIgnored = includeIgnored
    )

    var stagedCount = 0
    var unstagedCount = 0
    var untrackedCount = 0
    var ignoredCount = 0
    var conflictedCount = 0
    val entries = ArrayList<GitStatusEntry>(minOf(statuses.size, limit))
    val repositoryRootPath = repository.root.path

    for ((index, status) in statuses.withIndex()) {
      if (status.getStagedStatus() != null) stagedCount += 1
      if (status.getUnStagedStatus() != null) unstagedCount += 1
      if (status.isUntracked()) untrackedCount += 1
      if (status.isIgnored()) ignoredCount += 1
      if (status.isConflicted()) conflictedCount += 1

      if (index < limit) {
        entries.add(toGitStatusEntry(repositoryRootPath, status))
      }
    }

    val totalEntries = statuses.size

    return RepositoryGitStatus(
      repositoryPathRelativeToProject = repository.repositoryPathRelativeToProject,
      currentBranch = repository.currentBranch,
      isClean = totalEntries == 0,
      totalEntries = totalEntries,
      hasMoreEntries = totalEntries > entries.size,
      stagedCount = stagedCount,
      unstagedCount = unstagedCount,
      untrackedCount = untrackedCount,
      ignoredCount = ignoredCount,
      conflictedCount = conflictedCount,
      entries = entries
    )
  }

  private fun toGitStatusEntry(repositoryRootPath: String, status: GitFileStatus): GitStatusEntry {
    return GitStatusEntry(
      pathRelativeToRepository = relativePathInRepository(repositoryRootPath, status.path.path),
      indexStatus = status.index.toString(),
      workTreeStatus = status.workTree.toString(),
      originalPathRelativeToRepository = status.origPath?.let { relativePathInRepository(repositoryRootPath, it.path) }
    )
  }

  @Throws(VcsException::class)
  private fun readCurrentBranchName(project: Project, repositoryRoot: VirtualFile): String? {
    val handler = GitLineHandler(project, repositoryRoot, GitCommand.REV_PARSE)
    handler.addParameters("--abbrev-ref", "HEAD")
    handler.setSilent(true)

    val output = Git.getInstance().runCommand(handler).getOutputOrThrow().trim()
    return if (output.isBlank() || output == "HEAD") null else output
  }

  private fun relativePathInRepository(repositoryRootPath: String, filePath: String): String {
    val normalizedRoot = repositoryRootPath.replace('\\', '/').trimEnd('/')
    val normalizedPath = filePath.replace('\\', '/')

    return when {
      normalizedPath == normalizedRoot -> ""
      normalizedPath.startsWith("$normalizedRoot/") -> normalizedPath.removePrefix("$normalizedRoot/")
      else -> normalizedPath
    }
  }

  private fun repositoryRootPath(repository: GitRepository): Path {
    return Path.of(repository.root.path).normalize()
  }
}
