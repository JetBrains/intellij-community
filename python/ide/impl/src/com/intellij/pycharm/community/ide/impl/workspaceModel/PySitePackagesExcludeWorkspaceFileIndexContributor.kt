// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.workspaceModel

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.PythonSdkType

internal class PySitePackagesExcludeWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity> {
  override val entityClass: Class<SdkEntity> get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (SdkType.findByName(entity.type) !is PythonSdkType) {
      return
    }

    val classRoots = entity.roots.filter { it.type == SdkRootTypeId.CLASSES }.map { it.url }.toSet()
    classRoots.forEach { url ->
      val sitePackages = url.append(PyNames.SITE_PACKAGES)
      if (sitePackages !in classRoots) {
        registrar.registerExcludedRoot(sitePackages, entity)
      }
      val distPackages = url.append(PyNames.DIST_PACKAGES)
      if (distPackages !in classRoots) {
        registrar.registerExcludedRoot(distPackages, entity)
      }
    }
  }
}
