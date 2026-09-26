// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.externalIndex.workspace

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

/**
 * Indexes non-project files opened in the welcome screen.
 *
 * See [com.intellij.python.externalIndex.PyExternalFilesIndexService]
 */
internal class PyExternalFilesIndexContributor : WorkspaceFileIndexContributor<PyExternalIndexedFileEntity> {
  override val entityClass: Class<PyExternalIndexedFileEntity> = PyExternalIndexedFileEntity::class.java

  override fun registerFileSets(entity: PyExternalIndexedFileEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    registrar.registerFileSet(entity.file, WorkspaceFileKind.CUSTOM, entity, null)
  }
}
