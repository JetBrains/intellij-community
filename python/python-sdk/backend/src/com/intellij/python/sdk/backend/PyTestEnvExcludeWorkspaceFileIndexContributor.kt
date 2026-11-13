// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.ProjectRootEntity

/** The directories where tox and nox keep the virtual environments of their test sessions. */
private val TEST_ENV_DIRECTORY_NAMES: Array<String> = arrayOf(".tox", ".nox")

/**
 * Excludes each directory of [TEST_ENV_DIRECTORY_NAMES] at any depth below the root of an entity.
 * A contributor serves one entity class, so each kind of root has its own subclass.
 */
internal sealed class PyTestEnvExcludeContributor<E : WorkspaceEntity>(
  override val entityClass: Class<E>,
  private val root: (E) -> VirtualFileUrl,
) : WorkspaceFileIndexContributor<E> {
  override fun registerFileSets(entity: E, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerExclusionCondition(root(entity), testEnvExclusionCondition(), entity)
  }
}

internal class PyTestEnvExcludeProjectRootContributor :
  PyTestEnvExcludeContributor<ProjectRootEntity>(ProjectRootEntity::class.java, { it.root })

/** An old project can have no [ProjectRootEntity], and this contributor covers it. */
internal class PyTestEnvExcludeContentRootContributor :
  PyTestEnvExcludeContributor<ContentRootEntity>(ContentRootEntity::class.java, { it.url })

private fun testEnvExclusionCondition(): TestEnvExclusionCondition {
  val manager = VirtualFileManager.getInstance()
  return TestEnvExclusionCondition(IntArray(TEST_ENV_DIRECTORY_NAMES.size) { manager.storeName(TEST_ENV_DIRECTORY_NAMES[it]) })
}

/** Compares the name id of the file and does not resolve a path on the disk. */
private class TestEnvExclusionCondition(private val nameIds: IntArray) : WorkspaceFileSetExclusionCondition {
  override fun shouldExclude(file: VirtualFile): Boolean {
    return if (file is VirtualFileSystemEntry) file.nameId in nameIds else file.name in TEST_ENV_DIRECTORY_NAMES
  }

  override fun equals(other: Any?): Boolean = other is TestEnvExclusionCondition && nameIds.contentEquals(other.nameIds)

  override fun hashCode(): Int = nameIds.contentHashCode()
}
