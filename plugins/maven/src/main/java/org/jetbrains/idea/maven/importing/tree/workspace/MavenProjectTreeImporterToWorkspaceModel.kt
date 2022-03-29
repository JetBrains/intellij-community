// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleByEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.MavenProjectImporterBase
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterLegacyImpl
import org.jetbrains.idea.maven.importing.configurers.MavenModuleConfigurer
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenProjectTreeImporter
import org.jetbrains.idea.maven.importing.tree.ModuleData
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.*

class MavenProjectTreeImporterToWorkspaceModel(
  private val mavenProjectsTree: MavenProjectsTree,
  private val projectsToImportWithChanges: Map<MavenProject, MavenProjectChanges>,
  private val mavenImportingSettings: MavenImportingSettings,
  ideModelsProvider: IdeModifiableModelsProvider,
  private val project: Project
) : MavenProjectImporterBase(mavenProjectsTree, mavenImportingSettings, projectsToImportWithChanges) {

  private val modelsProvider = MavenProjectTreeImporter.getModelProvider(ideModelsProvider, project)
  private val createdModulesList = ArrayList<Module>()
  private val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val contextProvider = MavenProjectImportContextProvider(project, mavenProjectsTree,
                                                                  projectsToImportWithChanges, mavenImportingSettings)

  override fun importProject(): List<MavenProjectsProcessorTask> {
    val startTime = System.currentTimeMillis()
    val postTasks = ArrayList<MavenProjectsProcessorTask>()
    val context = contextProvider.context
    if (context.hasChanges) {
      importModules(context, postTasks)
      scheduleRefreshResolvedArtifacts(postTasks)
    }
    LOG.info("[maven import] applying models to workspace model took ${System.currentTimeMillis() - startTime}ms")
    return postTasks
  }

  private fun importModules(context: MavenModuleImportContext, postTasks: ArrayList<MavenProjectsProcessorTask>) {
    val builder = WorkspaceEntityStorageBuilder.create()

    val createdModuleIds = ArrayList<Pair<MavenModuleImportData, ModuleId>>()
    val mavenFolderHolderByMavenId = TreeMap<String, MavenImportFolderHolder>()

    for (importData in context.allModules) {
      val moduleEntity = WorkspaceModuleImporter(
        importData, virtualFileUrlManager, builder, mavenImportingSettings, mavenFolderHolderByMavenId, project
      ).importModule()
      createdModuleIds.add(importData to moduleEntity.persistentId())
    }

    val moduleImportDataList = mutableListOf<ModuleImportData>()
    MavenUtil.invokeAndWaitWriteAction(project) {
      WorkspaceModel.getInstance(project).updateProjectModel { current ->
        current.replaceBySource(
          { (it as? JpsImportedEntitySource)?.externalSystemId == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID }, builder)
      }
      val storage = WorkspaceModel.getInstance(project).entityStorage.current
      for ((importData, moduleId) in createdModuleIds) {
        val entity = storage.resolve(moduleId)
        if (entity == null) continue
        val module = storage.findModuleByEntity(entity)
        if (module != null) {
          createdModulesList.add(module)
          moduleImportDataList.add(ModuleImportData(module, importData))
        }
      }
    }

    facetImport(moduleImportDataList, context, postTasks)

    MavenUtil.invokeAndWaitWriteAction(project) { modelsProvider.dispose() }
    configureMavenProjects(moduleImportDataList, project)
  }

  private fun facetImport(moduleImportDataList: MutableList<ModuleImportData>,
                          context: MavenModuleImportContext,
                          postTasks: List<MavenProjectsProcessorTask>) {
    val modifiableModelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
    try {
      for (importData in moduleImportDataList) {
        configFacet(importData, context, modifiableModelsProvider, postTasks)
      }
    } finally {
      MavenUtil.invokeAndWaitWriteAction(project) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring { modifiableModelsProvider.commit() }
      }
    }
  }

  private fun configFacet(importData: ModuleImportData,
                          context: MavenModuleImportContext,
                          modifiableModelsProvider: IdeModifiableModelsProvider,
                          postTasks: List<MavenProjectsProcessorTask>) {
    if (importData.mavenModuleImportData.moduleData.type == MavenModuleType.AGGREGATOR_MAIN_TEST) return

    val mavenProject = importData.mavenModuleImportData.mavenProject
    val mavenProjectChanges = projectsToImportWithChanges.get(mavenProject)
    if (mavenProjectChanges == null || !mavenProjectChanges.hasChanges()) return

    if (mavenProject.suitableImporters.isEmpty()) return

    val mavenModuleImporter = MavenModuleImporter(
      importData.module, mavenProjectsTree,
      mavenProject,
      mavenProjectChanges,
      context.moduleNameByProject,
      mavenImportingSettings,
      modelsProvider,
      importData.mavenModuleImportData.moduleData.type
    )

    val rootModelAdapter = MavenRootModelAdapter(MavenRootModelAdapterLegacyImpl(mavenProject, importData.module, modelsProvider))
    val mapToOldImportModel = mapToOldImportModel(importData)
    MavenProjectTreeImporter.configModule(mapToOldImportModel, mavenModuleImporter, rootModelAdapter)

    mavenModuleImporter.setModifiableModelsProvider(modifiableModelsProvider);

    mavenModuleImporter.preConfigFacets()
    mavenModuleImporter.configFacets(postTasks)
    mavenModuleImporter.postConfigFacets()
  }

  private fun mapToOldImportModel(importData: ModuleImportData): org.jetbrains.idea.maven.importing.tree.MavenModuleImportData {
    val mavenModuleImportData = importData.mavenModuleImportData
    return org.jetbrains.idea.maven.importing.tree.MavenModuleImportData(
      mavenModuleImportData.mavenProject,
      ModuleData(importData.module, mavenModuleImportData.moduleData.type, mavenModuleImportData.moduleData.javaVersionHolder, true),
      mavenModuleImportData.dependencies,
      mavenModuleImportData.changes
    )
  }

  private fun configureMavenProjects(moduleImportDataList: List<ModuleImportData>, project: Project) {
    if (MavenUtil.isLinearImportEnabled()) return

    val configurers = MavenModuleConfigurer.getConfigurers()
    MavenUtil.runInBackground(project, MavenProjectBundle.message("command.name.configuring.projects"), false
    ) { indicator: MavenProgressIndicator ->
      var count = 0f
      val startTime = System.currentTimeMillis()
      val size = moduleImportDataList.size
      LOG.info("[maven import] applying " + configurers.size + " configurers to " + size + " Maven projects")
      for (moduleIMportData in moduleImportDataList) {
        indicator.setFraction((count++ / size).toDouble())
        indicator.setText2(MavenProjectBundle.message("progress.details.configuring.module", moduleIMportData.module.name))
        for (configurer in configurers) {
          configurer.configure(moduleIMportData.mavenModuleImportData.mavenProject, project, moduleIMportData.module)
        }
      }
      LOG.info("[maven import] configuring projects took " + (System.currentTimeMillis() - startTime) + "ms")
    }
  }

  override val createdModules: List<Module>
    get() = createdModulesList

  companion object {
    private val LOG = logger<MavenProjectTreeImporterToWorkspaceModel>()
  }
}

class ModuleImportData(
  val module: Module,
  val mavenModuleImportData: MavenModuleImportData
)