// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(PyInternalExecApi::class)

package com.intellij.python.junit5Tests.unit.evolution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.FacetEntityTypeId
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.python.pyproject.model.evolution.inMemoryFacetChanges
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A module that names an interpreter has the Python facet in memory, so Project Structure shows it as a Python module,
 * and the `.iml` keeps only the interpreter library entry. A saved facet of such a module, such as one that the user
 * added with "+", moves to memory. A saved facet of a module without an interpreter stays: it alone makes the module a
 * Python project.
 */
internal class PyInterpreterFacetsTest {
  private val saved = NonPersistentEntitySource
  private val inMemory = object : EntitySource {}
  private val entry = LibraryDependency(LibraryId("Python: . native:uv:.venv", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.COMPILE)

  @Test
  fun `a module with an interpreter gets a facet`() {
    val storage = storage(dependencies = listOf(entry))
    val changes = inMemoryFacetChanges(storage, inMemory)
    assertThat(changes.modulesToAdd.map { it.name }).containsExactly("module")
    assertThat(changes.facetsToRemove).isEmpty()
    assertThat(changes.facetsToMove).isEmpty()
  }

  @Test
  fun `a saved facet of a module with an interpreter moves to memory`() {
    val storage = storage(dependencies = listOf(entry), facetSource = saved)
    val changes = inMemoryFacetChanges(storage, inMemory)
    assertThat(changes.modulesToAdd).isEmpty()
    assertThat(changes.facetsToMove).hasSize(1)
    assertThat(changes.facetsToRemove).isEmpty()
  }

  @Test
  fun `a module with its facet in memory changes nothing`() {
    val storage = storage(dependencies = listOf(entry), facetSource = inMemory)
    val changes = inMemoryFacetChanges(storage, inMemory)
    assertThat(changes.modulesToAdd).isEmpty()
    assertThat(changes.facetsToRemove).isEmpty()
    assertThat(changes.facetsToMove).isEmpty()
  }

  @Test
  fun `a module without an interpreter loses its facet in memory and keeps a saved one`() {
    val inMemoryStorage = storage(dependencies = emptyList(), facetSource = inMemory)
    assertThat(inMemoryFacetChanges(inMemoryStorage, inMemory).facetsToRemove).hasSize(1)
    val savedStorage = storage(dependencies = emptyList(), facetSource = saved)
    assertThat(inMemoryFacetChanges(savedStorage, inMemory).facetsToRemove).isEmpty()
    assertThat(inMemoryFacetChanges(savedStorage, inMemory).facetsToMove).isEmpty()
  }

  private fun storage(dependencies: List<ModuleDependencyItem>, facetSource: EntitySource? = null): MutableEntityStorage =
    MutableEntityStorage.create().apply {
      addEntity(ModuleEntity("module", dependencies + ModuleSourceDependency, saved) {
        if (facetSource != null) {
          moduleSettings = listOf(FacetEntity("Python", FacetEntityTypeId(PYTHON_FACET_ID), facetSource) {
            configurationXmlTag = """<configuration sdkName="" />"""
          })
        }
      })
    }
}
