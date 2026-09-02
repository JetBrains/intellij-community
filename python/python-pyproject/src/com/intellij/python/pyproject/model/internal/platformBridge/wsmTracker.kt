package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.python.pyproject.model.internal.workspaceBridge.isPythonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CheckReturnValue


/**
 * Tracks workspace model of [project] for events (see [changesToTrack]).
 * Calls [onWsmChanged] if one happens. Be sure to cancel returned job when not needed.
 *
 * The argument of [onWsmChanged] holds every directory that stopped being excluded. The scanning pass never
 * descends into an excluded directory, so the VFS does not know its content. Such a directory therefore needs
 * a subtree load before the filename index can report its `pyproject.toml` (PY-91841).
 */
@CheckReturnValue
fun CoroutineScope.createWsmTracker(project: Project, onWsmChanged: (Set<VirtualFile>) -> Unit): Job =
  launch {
    project.workspaceModel.eventLog.collect { event ->
      val hasChanges = changesToTrack.any { (entityCls, check) ->
        event.getChanges(entityCls).any { check(it) }
      }
      if (hasChanges) {
        val unExcluded = event.getChanges(ExcludeUrlEntity::class.java)
          .filterIsInstance<EntityChange.Removed<ExcludeUrlEntity>>()
          .mapNotNullTo(LinkedHashSet()) { it.oldEntity.url.virtualFile }
        onWsmChanged(unExcluded)
      }
    }
  }


private val changesToTrack: Map<Class<out WorkspaceEntity>, (EntityChange<*>) -> Boolean> =
  mapOf(
    ExcludeUrlEntity::class.java to {
      when (it) {
        // A rebuild writes an exclusion of its own, and that write must not start a new rebuild.
        is EntityChange.Added -> !it.newEntity.entitySource.isPythonEntity
        // A removal is always kept. The scanning pass skipped the directory while it was excluded, so the VFS
        // may not know its content, whoever removed the exclusion.
        is EntityChange.Removed -> true
        is EntityChange.Replaced -> false
      }
    },
    ModuleEntity::class.java to {
      when (it) {
        is EntityChange.Added -> !it.newEntity.entitySource.isPythonEntity // New module and not python
        is EntityChange.Replaced, is EntityChange.Removed -> false
      }
    }
  )
