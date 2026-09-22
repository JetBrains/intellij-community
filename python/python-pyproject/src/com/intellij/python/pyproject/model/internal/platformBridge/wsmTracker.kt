// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.python.pyproject.model.internal.workspaceBridge.isPythonEntity

/**
 * Returns a rebuild request, or `null` when this event changes nothing the model reads.
 * The request includes directories that stopped being excluded and need their subtrees loaded into the VFS.
 *
 * The model reads two things from the workspace model: the set of excluded paths, and the set of module
 * names. An event that leaves both sets equal cannot change the model, so it must not cost a build.
 */
internal fun VersionedStorageChange.toRebuildRequest(): RebuildRequest? {
  val added = LinkedHashMap<String, ExcludeUrlEntity>()
  val removed = LinkedHashMap<String, ExcludeUrlEntity>()
  for (change in getChanges(ExcludeUrlEntity::class.java)) {
    when (change) {
      is EntityChange.Added -> added[change.newEntity.url.url] = change.newEntity
      is EntityChange.Removed -> removed[change.oldEntity.url.url] = change.oldEntity
      is EntityChange.Replaced -> {
        removed[change.oldEntity.url.url] = change.oldEntity
        added[change.newEntity.url.url] = change.newEntity
      }
    }
  }

  // A URL in both maps stays excluded, including after a move to another content root or a source change.
  // This pairing requires `collectExcludedPaths` to read the union over all content roots.
  val unExcluded = removed.filterKeys { it !in added }
  val newlyExcluded = added.filterKeys { it !in removed }

  if (unExcluded.isNotEmpty()) {
    val directories = unExcluded.values.mapNotNullTo(LinkedHashSet()) { it.url.virtualFile }
    return RebuildRequest(directories, describe("no longer excluded", unExcluded.keys))
  }
  if (newlyExcluded.isNotEmpty()) {
    // A rebuild excludes a virtualenv of its own, and that write must not start a new rebuild.
    val fromPlatform = newlyExcluded.filterValues { !it.entitySource.isPythonEntity }
    if (fromPlatform.isNotEmpty()) {
      return RebuildRequest(emptySet(), describe("newly excluded", fromPlatform.keys))
    }
  }

  // A module that the platform added. A module of this model carries a python entity source, and a build that
  // reacted to its own module would never stop.
  val newModule = getChanges(ModuleEntity::class.java)
    .filterIsInstance<EntityChange.Added<ModuleEntity>>()
    .firstOrNull { !it.newEntity.entitySource.isPythonEntity }
  if (newModule != null) {
    return RebuildRequest(emptySet(), "workspace model, the platform added the module '${newModule.newEntity.name}'")
  }
  return null
}

private fun describe(what: String, urls: Set<String>): String =
  "workspace model, ${urls.size} url $what, first '${urls.first()}'"
