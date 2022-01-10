// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleNameMapper
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenProjectImporterToWorkspaceModel(
  private val mavenProjectsTree: MavenProjectsTree,
  private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  private val mavenImportingSettings: MavenImportingSettings,
  private val dummyModuleId: ModuleId?,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val project: Project
): MavenProjectImporterBase(mavenProjectsTree, mavenImportingSettings, projectsToImportWithChanges) {
  private val createdModulesList = ArrayList<Module>()

  override fun importProject(): List<MavenProjectsProcessorTask> {
    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    if (projectsToImportHaveChanges()) {
      val builder = WorkspaceEntityStorageBuilder.create()
      importModules(builder)
      scheduleRefreshResolvedArtifacts(postTasks)
    }
    return postTasks
  }

  private fun importModules(builder: WorkspaceEntityStorageBuilder) {
    val allProjects = myProjectsTree.projects.toMutableSet()
    allProjects.addAll(projectsToImportWithChanges.keys)
    val moduleEntities = ArrayList<ModuleEntity>()
    val mavenProjectToModuleName = HashMap<MavenProject, String>()
    MavenModuleNameMapper.map(allProjects, emptyMap(), mavenProjectToModuleName, HashMap(), mavenImportingSettings.dedicatedModuleDir)
    for (mavenProject in allProjects) {
      val moduleName = mavenProjectToModuleName.getValue(mavenProject)
      val moduleEntity = WorkspaceModuleImporter(mavenProject, virtualFileUrlManager, mavenProjectsTree, builder,
                                                 mavenImportingSettings, mavenProjectToModuleName, project).importModule()
      moduleEntities.add(moduleEntity)
    }
    MavenUtil.invokeAndWaitWriteAction(project) {
      WorkspaceModel.getInstance(project).updateProjectModel { current ->
        current.replaceBySource({ (it as? JpsImportedEntitySource)?.externalSystemId == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID }, builder)
      }
      val storage = WorkspaceModel.getInstance(project).entityStorage.current
      moduleEntities.mapNotNullTo(createdModulesList) { storage.findModuleByEntity(it) }
    }
  }

  override val createdModules: List<Module>
    get() = createdModulesList
}