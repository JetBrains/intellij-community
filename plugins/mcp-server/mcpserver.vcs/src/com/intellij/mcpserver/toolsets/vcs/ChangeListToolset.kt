// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.vcs

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.milliseconds

class ChangeListToolset : McpToolset {

  @McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Retrieves the changelists of the project.
    |A changelist is a named group of pending (not yet committed) changes; every new modification is placed into the active changelist.
    |Use this tool to see how pending changes are grouped and which changelist is active.
  """)
  suspend fun get_changelists(
    @McpDescription("Whether to include project-relative paths of the changed files of each changelist")
    includeFiles: Boolean = false,
    @McpDescription("Maximum number of file paths returned per changelist when `includeFiles` is true")
    maxFilesPerChangelist: Int = 100,
  ): ChangeListsResult {
    if (maxFilesPerChangelist <= 0) mcpFail("maxFilesPerChangelist must be > 0")
    val project = currentCoroutineContext().project
    val changeListManager = changeListManager(project)

    // pick up files modified just before this call
    awaitChangeListUpdate(changeListManager)

    val projectDirectory = project.projectDirectory
    val changeLists = changeListManager.changeLists.map { changeList ->
      val changes = changeList.changes
      ChangeListInfo(
        name = changeList.name,
        id = changeList.id,
        comment = changeList.comment?.takeIf { it.isNotBlank() },
        isActive = changeList.isDefault,
        isReadOnly = changeList.isReadOnly,
        changesCount = changes.size,
        files = if (includeFiles) changes.take(maxFilesPerChangelist).map { projectDirectory.relativizeIfPossible(ChangesUtil.getFilePath(it)) } else null,
        hasMoreFiles = if (includeFiles && changes.size > maxFilesPerChangelist) true else null,
      )
    }
    return ChangeListsResult(changeLists)
  }

  @McpToolHints(idempotentHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Creates a changelist with the given name, or returns the existing one if a changelist with this name already exists.
    |Pass `activate=true` to make it the active changelist, so that all subsequent file modifications are placed into it.
    |Use this tool to separate the changes of the current task from other pending changes in the project.
    |Note: activation affects only future modifications; to group changes that already exist, use `move_changes_to_changelist`.
    |When activation switches the active changelist, the result includes `previousActiveChangeList`;
    |pass it to `set_active_changelist` later to restore the user's active changelist.
  """)
  suspend fun create_changelist(
    @McpDescription("Name of the changelist")
    name: String,
    @McpDescription("Optional description of the changelist")
    comment: String? = null,
    @McpDescription("Whether to make the changelist active, so that new changes are placed into it")
    activate: Boolean = false,
  ): CreateChangeListResult {
    val changeListName = name.trim()
    if (changeListName.isEmpty()) mcpFail("Changelist name must not be blank")
    val project = currentCoroutineContext().project
    val changeListManager = changeListManager(project)

    val existing = changeListManager.findChangeList(changeListName)
    val previousActive = changeListManager.defaultChangeList.name
    val changeList = existing ?: changeListManager.addChangeList(changeListName, comment?.takeIf { it.isNotBlank() })
    if (activate && !changeList.isDefault) {
      changeListManager.setDefaultChangeList(changeList, false)
    }
    return CreateChangeListResult(
      name = changeList.name,
      id = changeList.id,
      created = existing == null,
      isActive = activate || changeList.isDefault,
      previousActiveChangeList = if (activate) previousActive.takeIf { it != changeList.name } else null,
    )
  }

  @McpToolHints(idempotentHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Makes the changelist with the given name active: all new modifications will be placed into it.
    |Fails when the changelist does not exist; use `create_changelist` to create and activate a changelist in one call.
    |Use this tool to restore the previously active changelist when the current task is finished.
  """)
  suspend fun set_active_changelist(
    @McpDescription("Name of the changelist to make active")
    name: String,
  ): SetActiveChangeListResult {
    val changeListName = name.trim()
    if (changeListName.isEmpty()) mcpFail("Changelist name must not be blank")
    val project = currentCoroutineContext().project
    val changeListManager = changeListManager(project)

    val changeList = changeListManager.findChangeList(changeListName)
                     ?: mcpFail("Changelist '$changeListName' not found")
    val previousActive = changeListManager.defaultChangeList.name
    if (!changeList.isDefault) {
      changeListManager.setDefaultChangeList(changeList, false)
    }
    return SetActiveChangeListResult(
      name = changeList.name,
      previousActiveChangeList = previousActive.takeIf { it != changeList.name },
    )
  }

  @McpTool
  @McpDescription("""
    |Moves the pending changes of the specified files to the given changelist.
    |Use this tool to group changes that were already made (for example, by the current task) into a dedicated changelist;
    |it works regardless of which changelist was active while the files were modified, and does not change the active changelist.
    |By default the target changelist is created when it does not exist.
    |Files that have no pending change are reported in `filesWithoutPendingChanges`; the call fails when no changes were moved at all.
  """)
  suspend fun move_changes_to_changelist(
    @McpDescription("Project-relative paths of the changed files to move")
    files: List<String>,
    @McpDescription("Name of the target changelist")
    changeListName: String,
    @McpDescription("Whether to create the target changelist when it does not exist")
    createIfMissing: Boolean = true,
    @McpDescription("Optional description applied when the target changelist is created by this call")
    comment: String? = null,
  ): MoveChangesResult {
    val targetName = changeListName.trim()
    if (targetName.isEmpty()) mcpFail("Changelist name must not be blank")
    if (files.isEmpty()) mcpFail("`files` must not be empty")
    val project = currentCoroutineContext().project
    val changeListManager = changeListManager(project)

    val existingTarget = changeListManager.findChangeList(targetName)
    if (existingTarget == null && !createIfMissing) {
      mcpFail("Changelist '$targetName' not found. Pass createIfMissing=true to create it")
    }

    // pick up files modified just before this call
    awaitChangeListUpdate(changeListManager)

    val requestedFiles = files.distinct().map { path ->
      path to VcsUtil.getFilePath(project.resolveInProject(path).pathString, false)
    }
    var changesByPath = requestedFiles.associate { (path, filePath) -> path to changeListManager.getChange(filePath) }

    val unresolved = requestedFiles.filter { (path, _) -> changesByPath[path] == null }
    if (unresolved.isNotEmpty()) {
      // the files may not have been rescanned yet: mark them dirty and wait for one more update
      VcsDirtyScopeManager.getInstance(project).filePathsDirty(unresolved.map { it.second }, null)
      awaitChangeListUpdate(changeListManager)
      changesByPath = requestedFiles.associate { (path, filePath) -> path to changeListManager.getChange(filePath) }
    }

    val movedFiles = requestedFiles.map { it.first }.filter { changesByPath[it] != null }
    val filesWithoutChanges = requestedFiles.map { it.first }.filter { changesByPath[it] == null }
    if (movedFiles.isEmpty()) {
      mcpFail("No pending changes found for: ${filesWithoutChanges.joinToString(", ")}")
    }

    val targetList = existingTarget ?: changeListManager.addChangeList(targetName, comment?.takeIf { it.isNotBlank() })
    changeListManager.moveChangesTo(targetList, movedFiles.mapNotNull { changesByPath[it] })

    return MoveChangesResult(
      changeListName = targetList.name,
      createdChangeList = existingTarget == null,
      movedFiles = movedFiles,
      filesWithoutPendingChanges = filesWithoutChanges.takeIf { it.isNotEmpty() },
    )
  }

  @McpToolHints(destructiveHint = TRUE, openWorldHint = FALSE)
  @McpTool
  @McpDescription("""
    |Deletes the changelist with the given name.
    |Pending changes of the deleted changelist are moved to the active changelist.
    |The active changelist cannot be deleted: make another changelist active first (see `create_changelist`).
  """)
  suspend fun delete_changelist(
    @McpDescription("Name of the changelist to delete")
    name: String,
  ): DeleteChangeListResult {
    val changeListName = name.trim()
    if (changeListName.isEmpty()) mcpFail("Changelist name must not be blank")
    val project = currentCoroutineContext().project
    val changeListManager = changeListManager(project)

    val changeList = changeListManager.findChangeList(changeListName)
                     ?: mcpFail("Changelist '$changeListName' not found")
    if (changeList.isDefault) mcpFail("Cannot delete the active changelist '$changeListName'. Make another changelist active first")
    if (changeList.isReadOnly) mcpFail("Cannot delete the read-only changelist '$changeListName'")

    val movedChangesCount = changeList.changes.size
    changeListManager.removeChangeList(changeList)
    return DeleteChangeListResult(
      name = changeList.name,
      movedChangesCount = movedChangesCount,
      movedTo = if (movedChangesCount > 0) changeListManager.defaultChangeList.name else null,
    )
  }

  @Serializable
  data class ChangeListInfo(
    @property:McpDescription("Changelist name")
    val name: String,
    @property:McpDescription("Stable identifier of the changelist that survives renames")
    val id: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val comment: String? = null,
    @property:McpDescription("Whether this is the active changelist where new changes are placed")
    val isActive: Boolean,
    val isReadOnly: Boolean,
    @property:McpDescription("Number of pending changes in the changelist")
    val changesCount: Int,
    @property:McpDescription("Project-relative paths of the changed files. Present only when `includeFiles` is true")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val files: List<String>? = null,
    @property:McpDescription("True when `files` was truncated by `maxFilesPerChangelist`")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val hasMoreFiles: Boolean? = null,
  )

  @Serializable
  data class ChangeListsResult(
    val changeLists: List<ChangeListInfo>,
  )

  @Serializable
  data class CreateChangeListResult(
    @property:McpDescription("Changelist name")
    val name: String,
    @property:McpDescription("Stable identifier of the changelist that survives renames")
    val id: String,
    @property:McpDescription("True when a new changelist was created, false when a changelist with this name already existed")
    val created: Boolean,
    @property:McpDescription("Whether the changelist is now active, i.e. new changes are placed into it")
    val isActive: Boolean,
    @property:McpDescription("Name of the previously active changelist. Present only when this call switched the active changelist; pass it to `set_active_changelist` to restore")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val previousActiveChangeList: String? = null,
  )

  @Serializable
  data class SetActiveChangeListResult(
    @property:McpDescription("Name of the now-active changelist")
    val name: String,
    @property:McpDescription("Name of the previously active changelist; absent when the changelist was already active")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val previousActiveChangeList: String? = null,
  )

  @Serializable
  data class MoveChangesResult(
    @property:McpDescription("Name of the target changelist")
    val changeListName: String,
    @property:McpDescription("True when the target changelist was created by this call")
    val createdChangeList: Boolean,
    @property:McpDescription("Project-relative paths of the files whose changes were moved")
    val movedFiles: List<String>,
    @property:McpDescription("Requested files that had no pending change and were skipped")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val filesWithoutPendingChanges: List<String>? = null,
  )

  @Serializable
  data class DeleteChangeListResult(
    @property:McpDescription("Name of the deleted changelist")
    val name: String,
    @property:McpDescription("Number of pending changes moved out of the deleted changelist")
    val movedChangesCount: Int,
    @property:McpDescription("Name of the active changelist that received the moved changes")
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val movedTo: String? = null,
  )
}

/** Waits for the running [ChangeListManagerEx] update to finish; the callback fires immediately when no update is running. */
private suspend fun awaitChangeListUpdate(changeListManager: ChangeListManagerEx) {
  withTimeoutOrNull(Constants.MEDIUM_TIMEOUT_MILLISECONDS_VALUE.milliseconds) {
    suspendCancellableCoroutine { continuation ->
      changeListManager.invokeAfterUpdate(false) { continuation.resume(Unit) }
    }
  }
}

private fun changeListManager(project: Project): ChangeListManagerEx {
  val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  if (!changeListManager.areChangeListsEnabled()) {
    mcpFail("Changelists are disabled in this project (for example, Git repositories use the staging area commit mode)")
  }
  return changeListManager
}

private fun Path.relativizeIfPossible(filePath: FilePath): String {
  val nioPath = try {
    Path.of(filePath.path)
  }
  catch (_: InvalidPathException) {
    null
  } ?: return filePath.path
  return try {
    relativize(nioPath).pathString
  }
  catch (_: IllegalArgumentException) {
    filePath.path
  }
}
