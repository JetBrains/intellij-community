// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.tree.*
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.*

class MavenProjectTreeImporterToWorkspaceModel(
  projectsTree: MavenProjectsTree,
  projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  importingSettings: MavenImportingSettings,
  ideModelsProvider: IdeModifiableModelsProvider,
  project: Project
) : MavenProjectImporterBase(project, projectsTree, importingSettings, projectsToImportWithChanges, ideModelsProvider) {

  private val createdModulesList = ArrayList<Module>()
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val contextProvider = MavenProjectImportContextProvider(project, projectsTree,
                                                                  projectsToImportWithChanges, myImportingSettings)

  override fun importProject(): List<MavenProjectsProcessorTask> {

    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val context = contextProvider.context
    if (context.hasChanges) {
      importModules(context, postTasks)
      scheduleRefreshResolvedArtifacts(postTasks)
    }
    return postTasks

  }

  private fun importModules(context: MavenModuleImportContext, postTasks: ArrayList<MavenProjectsProcessorTask>) {
    val builder = WorkspaceEntityStorageBuilder.create()

    val createdModuleIds = ArrayList<Pair<MavenModuleImportData, ModuleId>>()
    val mavenFolderHolderByMavenId = TreeMap<String, MavenImportFolderHolder>()

    for (importData in context.allModules) {
      val moduleEntity = WorkspaceModuleImporter(
        importData, virtualFileUrlManager, builder, myImportingSettings, mavenFolderHolderByMavenId, myProject
      ).importModule()
      createdModuleIds.add(importData to moduleEntity.persistentId())
    }

    val legacyModuleImportDataList = mutableListOf<MavenModuleImportData>()
    MavenUtil.invokeAndWaitWriteAction(myProject) {
      WorkspaceModel.getInstance(myProject).updateProjectModel { current ->
        current.replaceBySource(
          { (it as? JpsImportedEntitySource)?.externalSystemId == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID }, builder)
      }
      val storage = WorkspaceModel.getInstance(myProject).entityStorage.current
      for ((importData, moduleId) in createdModuleIds) {
        val entity = storage.resolve(moduleId)
        if (entity == null) continue
        val module = storage.findModuleByEntity(entity)
        if (module != null) {
          createdModulesList.add(module)

          legacyModuleImportDataList.add(mapToLegacyImportModel(importData, module))
        }
      }
    }

    val configurer = MavenProjectTreeLegacyImporter.TreeModuleConfigurer(myProjectsTree, myImportingSettings, myModelsProvider)

    // todo configModule seems to repeat folders configuration
    val importers = configurer.configModules(legacyModuleImportDataList, context.moduleNameByProject)
    configFacets(importers, postTasks)

    MavenUtil.invokeAndWaitWriteAction(myProject) { myModelsProvider.dispose() }

  }

  private fun mapToLegacyImportModel(importData: MavenModuleImportData, module: Module): MavenModuleImportData {
    return importData.copy(
      moduleData = LegacyModuleData(module,
                                    importData.moduleData.type,
                                    importData.moduleData.javaVersionHolder,
                                    isNewModule = true))
  }

  override fun createdModules(): List<Module> {
    return createdModulesList
  }
}