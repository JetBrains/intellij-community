// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.ModuleSettingsContributor
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.project.MavenProject

class ModuleSettingsImporter {
   fun configureSettingsForModule(project: Project, moduleEntity: ModuleEntity, mavenProject: MavenProject) {
     val entitySource = calculateEntitySource(project, moduleEntity)
     ModuleSettingsContributor.EP_NAME.forEachExtensionSafe { settingsContributor ->
       settingsContributor.addSettings(project, moduleEntity, entitySource, MutableEntityStorage.create())
     }
     // TODO:: Only one usage in the plugins at EjbFacetImporter an zero at external plugins
     // setupFacet(f, mavenProject)
  }
  
  private fun calculateEntitySource(project: Project, moduleEntity: ModuleEntity): EntitySource {
    val moduleSource = moduleEntity.entitySource
    val externalSource = MavenRootModelAdapter.getMavenExternalSource()
    return when {
      moduleSource is JpsFileEntitySource ->
        JpsImportedEntitySource(moduleSource, externalSource.id, project.isExternalStorageEnabled)
      moduleSource is JpsImportedEntitySource && moduleSource.externalSystemId != externalSource.id ->
        JpsImportedEntitySource(moduleSource.internalFile, externalSource.id, project.isExternalStorageEnabled)
      else -> moduleSource
    }
  }
}