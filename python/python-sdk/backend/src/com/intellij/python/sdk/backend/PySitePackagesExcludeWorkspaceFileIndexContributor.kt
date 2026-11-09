// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.jetbrains.python.PyNames

/**
 * Excludes the `site-packages` of a Python SDK that the project uses, see [sitePackagesToExclude]. It is the legacy
 * flow: the interpreter library flow writes the exclusions into the interpreter library, see `PyInterpreterLibrarySync`.
 *
 * Only an SDK that a module refers to counts. The SDK table holds the SDKs of every project, and an unused virtual
 * environment must not hide the packages of the base interpreter that the project uses (PY-93818).
 */
internal class PySitePackagesExcludeWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity> {
  override val entityClass: Class<SdkEntity> get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (isInterpreterLibraryFlowEnabled()) return
    if (!storage.isUsedPythonSdk(entity)) return
    val kept = storage.entities<SdkEntity>().filter { storage.isUsedPythonSdk(it) }.flatMapTo(HashSet()) { it.classRoots() }
    for (url in sitePackagesToExclude(entity.classRoots(), kept)) {
      registrar.registerExcludedRoot(url, entity)
    }
  }

  /** A change of the use of an SDK, or of another used SDK, changes what this SDK excludes. */
  override val dependenciesOnOtherEntities: List<DependencyDescription<SdkEntity>>
    get() = listOf(DependencyDescription.OnReference(SdkId::class.java))

  private fun SdkEntity.classRoots() = roots.filter { it.type == SdkRootTypeId.CLASSES }.map { it.url }

  /** Whether [sdk] is a Python SDK that a module of the project refers to. */
  private fun EntityStorage.isUsedPythonSdk(sdk: SdkEntity): Boolean =
    sdk.type == PyNames.PYTHON_SDK_ID_NAME && hasReferrers(sdk.symbolicId)
}
