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
 * The first argument of [onWsmChanged] holds every directory that stopped being excluded. The scanning pass
 * never descends into an excluded directory, so the VFS does not know its content. Such a directory therefore
 * needs a subtree load before the filename index can report its `pyproject.toml` (PY-91841).
 *
 * The second argument names the change that woke the tracker. A rebuild writes to the workspace model itself,
 * so this reason tells a reader whether a build started another build.
 */
@CheckReturnValue
internal fun CoroutineScope.createWsmTracker(project: Project, onWsmChanged: (Set<VirtualFile>, String) -> Unit): Job =
  launch {
    project.workspaceModel.eventLog.collect { event ->
      val reason = changesToTrack.firstNotNullOfOrNull { (entityCls, check) ->
        event.getChanges(entityCls).firstOrNull { check(it) }?.let { describe(entityCls, it) }
      }
      if (reason != null) {
        val unExcluded = event.getChanges(ExcludeUrlEntity::class.java)
          .filterIsInstance<EntityChange.Removed<ExcludeUrlEntity>>()
          .mapNotNullTo(LinkedHashSet()) { it.oldEntity.url.virtualFile }
        onWsmChanged(unExcluded, reason)
      }
    }
  }

/**
 * Names one change for the log.
 *
 * The entity source is part of the name, because it tells a python entity of a rebuild from an entity that the
 * platform wrote.
 */
private fun describe(entityClass: Class<out WorkspaceEntity>, change: EntityChange<*>): String {
  val kind = when (change) {
    is EntityChange.Added -> "added"
    is EntityChange.Removed -> "removed"
    is EntityChange.Replaced -> "replaced"
  }
  val entity = change.newEntity ?: change.oldEntity
  val name = (entity as? ModuleEntity)?.name ?: (entity as? ExcludeUrlEntity)?.url?.url ?: "?"
  return "workspace model, ${entityClass.simpleName} $kind '$name', source ${entity?.entitySource?.javaClass?.simpleName}"
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
