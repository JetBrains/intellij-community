// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.pyproject.model.internal.workspaceBridge.isPythonEntity
import com.intellij.workspaceModel.ide.toPath
import java.nio.file.Path

/**
 * Returns a rebuild request, or `null` when the event needs no rebuild.
 * Changes to the effective project roots request a full scan.
 * Removed exclusions include directories whose subtrees must be loaded into the VFS.
 * Added or renamed platform modules request a rebuild to check for name clashes.
 */
internal fun VersionedStorageChange.toRebuildRequest(projectBasePath: Path): PendingRebuild? {
  if (projectRootsChanged(projectBasePath)) {
    return PendingRebuild.FullScan("workspace model, project roots changed")
  }

  val added = LinkedHashMap<VirtualFileUrl, ExcludeUrlEntity>()
  val removed = LinkedHashMap<VirtualFileUrl, ExcludeUrlEntity>()
  for (change in getChanges(ExcludeUrlEntity::class.java)) {
    when (change) {
      is EntityChange.Added -> added[change.newEntity.url] = change.newEntity
      is EntityChange.Removed -> removed[change.oldEntity.url] = change.oldEntity
      is EntityChange.Replaced -> {
        removed[change.oldEntity.url] = change.oldEntity
        added[change.newEntity.url] = change.newEntity
      }
    }
  }

  // A URL in both maps stays excluded, including after a move to another content root or a source change.
  // This pairing requires `collectExcludedPaths` to read the union over all content roots.
  val unExcluded = removed.filterKeys { it !in added }
  val newlyExcluded = added.filterKeys { it !in removed }

  if (unExcluded.isNotEmpty()) {
    val directories = unExcluded.values.mapNotNullTo(LinkedHashSet()) { it.url.virtualFile }
    return PendingRebuild.Directories(directories, describe("no longer excluded", unExcluded.keys))
  }
  if (newlyExcluded.isNotEmpty()) {
    // A rebuild excludes a virtualenv of its own, and that write must not start a new rebuild.
    val fromPlatform = newlyExcluded.filterValues { !it.entitySource.isPythonEntity }
    if (fromPlatform.isNotEmpty()) {
      return PendingRebuild.Directories(emptySet(), describe("newly excluded", fromPlatform.keys))
    }
  }

  for (change in getChanges(ModuleEntity::class.java)) {
    val module = when (change) {
      is EntityChange.Added -> change.newEntity
      is EntityChange.Removed -> null
      is EntityChange.Replaced -> change.newEntity.takeIf { it.name != change.oldEntity.name }
    }
    if (module != null && !module.entitySource.isPythonEntity) {
      return PendingRebuild.Directories(emptySet(), "workspace model, the platform added or renamed the module '${module.name}'")
    }
  }
  return null
}

/** Checks root membership only when a content root was added, removed, or changed its URL. */
private fun VersionedStorageChange.projectRootsChanged(projectBasePath: Path): Boolean {
  val rootsChanged = getChanges(ContentRootEntity::class.java).any { change ->
    when (change) {
      is EntityChange.Added, is EntityChange.Removed -> true
      is EntityChange.Replaced -> change.oldEntity.url != change.newEntity.url
    }
  }
  if (!rootsChanged) return false

  fun roots(storage: ImmutableEntityStorage): Set<Path> {
    val paths = storage.entities<ModuleEntity>().flatMap { it.contentRoots }.map { it.url.toPath() }
    return computeMinimalRoots(sequenceOf(projectBasePath) + paths)
  }
  return roots(storageBefore) != roots(storageAfter)
}

private fun describe(what: String, urls: Set<VirtualFileUrl>): String =
  "workspace model, ${urls.size} url $what, first '${urls.first().url}'"
