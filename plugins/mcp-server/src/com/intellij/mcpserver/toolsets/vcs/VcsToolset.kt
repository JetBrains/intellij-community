@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.vcs

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.currentCoroutineContext
import kotlin.io.path.Path

class VcsToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Searches for a commit based on the provided text or keywords in the project history.
        Useful for finding specific change sets or code modifications by commit messages or diff content.
        Takes a query parameter and returns the matching commit information.
        Returns matched commit hashes as a JSON array.
    """)
    suspend fun find_commit_by_message(
        @McpDescription("Text or keywords to search for in commit messages")
        text: String
    ): String {
        val project = currentCoroutineContext().project
        val queryText = text
        val matchingCommits = mutableListOf<String>()

        try {
            val vcs = ProjectLevelVcsManager.getInstance(project).allVcsRoots
                .mapNotNull { it.path }

            if (vcs.isEmpty()) {
                return "Error: No VCS configured for this project"
            }

            // Iterate over each VCS root to search for commits
            vcs.forEach { vcsRoot ->
                val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(vcsRoot)
                    ?: return@forEach

                val gitLog = GitHistoryUtils.history(project, repository.root)

                gitLog.forEach { commit ->
                    if (commit.fullMessage.contains(queryText, ignoreCase = true)) {
                        matchingCommits.add(commit.id.toString())
                    }
                }
            }

            // Check if any matches were found
            return if (matchingCommits.isNotEmpty()) {
                matchingCommits.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            } else {
                "No commits found matching the query: $queryText"
            }

        } catch (e: Exception) {
            // Handle any errors that occur during the search
            return "Error while searching commits: ${e.message}"
        }
    }

    @McpTool
    @McpDescription("""
        Retrieves the current version control status of files in the project.
        Use this tool to get information about modified, added, deleted, and moved files in your VCS (e.g., Git).
        Returns a JSON-formatted list of changed files, where each entry contains:
        - path: The file path relative to project root
        - type: The type of change (e.g., MODIFICATION, ADDITION, DELETION, MOVED)
        Returns an empty list ([]) if no changes are detected or VCS is not configured.
        Returns error "project dir not found" if project directory cannot be determined.
        Note: Works with any VCS supported by the IDE, but is most commonly used with Git
    """)
    suspend fun get_project_vcs_status(): String {
        val project = currentCoroutineContext().project
        val projectDir = project.projectDirectory

        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.allChanges
        val unversionedFiles = changeListManager.unversionedFilesPaths

        val result = mutableListOf<Map<String, String>>()

        // Process tracked changes
        changes.forEach { change ->
            val absolutePath = change.virtualFile?.path ?: change.afterRevision?.file?.path
            val changeType = change.type

            if (absolutePath != null) {
                try {
                    val relativePath = projectDir.relativize(Path(absolutePath)).toString()
                    result.add(mapOf("path" to relativePath, "type" to changeType.toString()))
                } catch (e: IllegalArgumentException) {
                    // Skip files outside project directory
                }
            }
        }

        // Process unversioned files
        unversionedFiles.forEach { file ->
            try {
                val relativePath = projectDir.relativize(Path(file.path)).toString()
                result.add(mapOf("path" to relativePath, "type" to "UNVERSIONED"))
            } catch (e: IllegalArgumentException) {
                // Skip files outside project directory
            }
        }

        return result.joinToString(",\n", prefix = "[", postfix = "]") {
            """{"path": "${it["path"]}", "type": "${it["type"]}"}"""
        }
    }
}