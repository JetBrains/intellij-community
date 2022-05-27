// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.idea.maven.project.MavenProject

open class WorkspaceModuleImporterBase(protected val builder: WorkspaceEntityStorageBuilder) {
  protected val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(EXTERNAL_SOURCE_ID)

  protected fun createModuleEntity(mavenProject: MavenProject,
                                   moduleName: String,
                                   dependencies: List<ModuleDependencyItem>,
                                   entitySource: EntitySource): ModuleEntity {
    val moduleEntity = builder.addModuleEntity(moduleName, dependencies, entitySource, ModuleTypeId.JAVA_MODULE)
    builder.addEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, entitySource) {
      module = moduleEntity
      externalSystem = EXTERNAL_SOURCE_ID
      linkedProjectPath = linkedProjectPath(mavenProject)
    }
    return moduleEntity
  }

  companion object {
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")

    val EXTERNAL_SOURCE_ID get() = ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID

    fun linkedProjectPath(mavenProject: MavenProject): String {
      return FileUtil.toSystemIndependentName(mavenProject.directory)
    }
  }
}