// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.python.pyproject.model.internal.workspaceBridge.isPythonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


/**
 * Tracks the workspace model of [project] for a change that the `pyproject.toml` model reads.
 * Calls [onWsmChanged] if one happens. Cancelling the calling scope stops the tracker.
 * Establishes the subscription before returning.
 *
 * The first argument of [onWsmChanged] holds every directory that stopped being excluded. The scanning pass
 * never descends into an excluded directory, so the VFS does not know its content. Such a directory therefore
 * needs a subtree load before the filename index can report its `pyproject.toml` (PY-91841).
 *
 * The second argument names the change that woke the tracker. A rebuild writes to the workspace model itself,
 * so this reason tells a reader whether a build started another build.
 */
internal fun CoroutineScope.createWsmTracker(project: Project, onWsmChanged: (Set<VirtualFile>, String) -> Unit): Job =
  launch(start = CoroutineStart.UNDISPATCHED) {
    project.workspaceModel.eventLog.collect { event ->
      val trigger = event.findTrigger() ?: return@collect
      onWsmChanged(trigger.directoriesToLoad, trigger.reason)
    }
  }

internal class Trigger(val directoriesToLoad: Set<VirtualFile>, val reason: String)

/**
 * Returns the change that must start a build, or `null` when this event changes nothing the model reads.
 *
 * The model reads two things from the workspace model: the set of excluded paths, and the set of module
 * names. An event that leaves both sets equal cannot change the model, so it must not cost a build.
 *
 * Internal for `PyWsmTriggerTest`, which feeds it a real event of a relocation.
 */
internal fun VersionedStorageChange.findTrigger(): Trigger? {
  val excludeChanges = getChanges(ExcludeUrlEntity::class.java)
  val added = excludeChanges.filterIsInstance<EntityChange.Added<ExcludeUrlEntity>>().associateBy { it.newEntity.url.url }
  val removed = excludeChanges.filterIsInstance<EntityChange.Removed<ExcludeUrlEntity>>().associateBy { it.oldEntity.url.url }

  // A url in both maps only moved to another content root. `ensureNoSrcIntersectsWithOtherRoots` relocates an
  // excluded url that way, and `collectExcludedPaths` reads the union over every content root. The set of
  // excluded paths is therefore the same after such a pair, and a build would repeat the previous build.
  // The directory also stays excluded, so a subtree load of it would fill the VFS with a build output.
  // This pairing is sound only while `collectExcludedPaths` reads one union over every content root.
  val unExcluded = removed.filterKeys { it !in added }
  val newlyExcluded = added.filterKeys { it !in removed }

  if (unExcluded.isNotEmpty()) {
    val directories = unExcluded.values.mapNotNullTo(LinkedHashSet()) { it.oldEntity.url.virtualFile }
    return Trigger(directories, describe("no longer excluded", unExcluded.keys))
  }
  if (newlyExcluded.isNotEmpty()) {
    // A rebuild excludes a virtualenv of its own, and that write must not start a new rebuild.
    val fromPlatform = newlyExcluded.filterValues { !it.newEntity.entitySource.isPythonEntity }
    if (fromPlatform.isNotEmpty()) {
      return Trigger(emptySet(), describe("newly excluded", fromPlatform.keys))
    }
  }

  // A module that the platform added. A module of this model carries a python entity source, and a build that
  // reacted to its own module would never stop.
  val newModule = getChanges(ModuleEntity::class.java)
    .filterIsInstance<EntityChange.Added<ModuleEntity>>()
    .firstOrNull { !it.newEntity.entitySource.isPythonEntity }
  if (newModule != null) {
    return Trigger(emptySet(), "workspace model, the platform added the module '${newModule.newEntity.name}'")
  }
  return null
}

private fun describe(what: String, urls: Set<String>): String =
  "workspace model, ${urls.size} url $what, first '${urls.first()}'"
