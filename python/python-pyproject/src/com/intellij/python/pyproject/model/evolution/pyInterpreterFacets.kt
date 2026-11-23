// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID

/** The Python facets that [inMemoryFacetChanges] adds, removes, and moves from the `.iml` to memory. */
internal class InMemoryFacetChanges(
  val modulesToAdd: List<ModuleEntity>,
  val facetsToRemove: List<FacetEntity>,
  val facetsToMove: List<FacetEntity>,
)

/**
 * The changes that give each module of [storage] that names an interpreter one Python facet, in memory with [source], so
 * Project Structure shows it as a Python module and the `.iml` keeps only the interpreter library entry:
 * - A module without a Python facet gets one.
 * - A saved Python facet moves to memory, such as one that the user added with "+". The others of the module go.
 * - A module that names no interpreter loses its facet in memory. Its saved facet stays: it alone makes the module a
 *   Python project.
 */
internal fun inMemoryFacetChanges(storage: EntityStorage, source: EntitySource): InMemoryFacetChanges {
  val modulesToAdd = mutableListOf<ModuleEntity>()
  val facetsToRemove = mutableListOf<FacetEntity>()
  val facetsToMove = mutableListOf<FacetEntity>()
  for (module in storage.entities<ModuleEntity>()) {
    val pythonFacets = module.facets.filter { it.typeId.name == PYTHON_FACET_ID }
    if (interpreterLibraryOf(module) == null) {
      facetsToRemove += pythonFacets.filter { it.entitySource == source }
      continue
    }
    // The one to keep: a facet in memory, else the first saved one, which moves.
    val kept = pythonFacets.firstOrNull { it.entitySource == source } ?: pythonFacets.firstOrNull()
    when {
      kept == null -> modulesToAdd += module
      kept.entitySource != source -> facetsToMove += kept
    }
    facetsToRemove += pythonFacets.filter { it != kept }
  }
  return InMemoryFacetChanges(modulesToAdd, facetsToRemove, facetsToMove)
}

/** The configuration of a Python facet in memory: no SDK, as the interpreter library entry names the interpreter. */
internal const val IN_MEMORY_FACET_CONFIGURATION: String = """<configuration sdkName="" />"""

/** The name of a Python facet in memory, as Project Structure shows it. */
internal const val PYTHON_FACET_NAME: String = "Python"
