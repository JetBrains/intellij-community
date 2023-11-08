// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.project.ArtifactExternalDependenciesImporter
import com.intellij.openapi.externalSystem.project.PackagingModel
import com.intellij.openapi.externalSystem.service.project.ArtifactExternalDependenciesImporterImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.packaging.artifacts.ArtifactModel
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.packaging.elements.PackagingElementResolvingContext
import com.intellij.packaging.impl.artifacts.DefaultPackagingElementResolvingContext
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator.*
import org.jetbrains.idea.maven.importing.workspaceModel.ARTIFACT_MODEL_KEY
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import java.util.concurrent.ConcurrentHashMap

private val FACET_DETECTION_DISABLED_KEY = Key.create<ConcurrentHashMap<MavenWorkspaceFacetConfigurator, Boolean>>("FACET_DETECTION_DISABLED_KEY")

@ApiStatus.Internal
interface MavenWorkspaceFacetConfigurator : MavenWorkspaceConfigurator {
  fun isApplicable(mavenProject: MavenProject): Boolean
  fun isFacetDetectionDisabled(project: Project): Boolean

  fun preProcess(storage: MutableEntityStorage,
                 module: ModuleEntity,
                 project: Project,
                 mavenProject: MavenProject,
                 artifactModel: ModifiableArtifactModel) = preProcess(storage, module)

  fun preProcess(storage: MutableEntityStorage, module: ModuleEntity) {
  }

  fun process(storage: MutableEntityStorage,
              module: ModuleEntity,
              project: Project,
              mavenProject: MavenProject,
              mavenTree: MavenProjectsTree,
              mavenProjectToModuleName: Map<MavenProject, String>,
              packagingModel: PackagingModel,
              postTasks: MutableList<MavenProjectsProcessorTask>,
              userDataHolder: UserDataHolderEx) {
  }

  private fun isFacetDetectionDisabled(context: Context<*>): Boolean {
    context.putUserDataIfAbsent(FACET_DETECTION_DISABLED_KEY, ConcurrentHashMap())
    return FACET_DETECTION_DISABLED_KEY[context].computeIfAbsent(this) { isFacetDetectionDisabled(context.project) }
  }

  override fun configureMavenProject(context: MutableMavenProjectContext) {
    if (isFacetDetectionDisabled(context)) return

    val project = context.project
    val mavenProjectWithModules = context.mavenProjectWithModules
    val mavenProject = mavenProjectWithModules.mavenProject
    if (!isApplicable(mavenProject)) return

    val modules = mavenProjectWithModules.modules
    for (moduleWithType in modules) {
      val moduleType = moduleWithType.type
      if (moduleType.containsCode) {
        val module = moduleWithType.module
        preProcess(context.storage, module, project, mavenProject, ARTIFACT_MODEL_KEY[context])
      }
    }
  }

  override fun beforeModelApplied(context: MutableModelContext) {
    if (isFacetDetectionDisabled(context)) return

    val project = context.project
    val artifactModel = ARTIFACT_MODEL_KEY[context]
    val resolvingContext = object : DefaultPackagingElementResolvingContext(project) {
      override fun getArtifactModel(): ArtifactModel {
        return artifactModel
      }
    }
    val packagingModel: PackagingModel = FacetPackagingModel(artifactModel, resolvingContext)
    val mavenProjectsWithModules = context.mavenProjectsWithModules
    val storage = context.storage
    val mavenTree = context.mavenProjectsTree
    val postTasks = mutableListOf<MavenProjectsProcessorTask>()

    fun mavenModuleTypeOrder(type: MavenModuleType): Int = when (type) {
      StandardMavenModuleType.SINGLE_MODULE -> 0
      StandardMavenModuleType.MAIN_ONLY -> 1
      StandardMavenModuleType.TEST_ONLY -> 2
      StandardMavenModuleType.COMPOUND_MODULE -> 3
      StandardMavenModuleType.AGGREGATOR -> 4
    }

    val mavenProjectToModuleName = mavenProjectsWithModules.associateBy({ it.mavenProject }, {
      it.modules.minByOrNull { moduleWithType -> mavenModuleTypeOrder(moduleWithType.type) }!!.module.name
    })

    for (mavenProjectWithModules in mavenProjectsWithModules) {
      val mavenProject = mavenProjectWithModules.mavenProject
      if (!isApplicable(mavenProject)) continue

      val modules = mavenProjectWithModules.modules

      for (moduleWithType in modules) {
        val moduleType = moduleWithType.type
        if (moduleType.containsCode) {
          val module = moduleWithType.module
          process(storage,
                  module,
                  project,
                  mavenProject,
                  mavenTree,
                  mavenProjectToModuleName,
                  packagingModel,
                  postTasks,
                  context)
        }
      }
    }
  }

  class FacetPackagingModel(private val myArtifactModel: ModifiableArtifactModel,
                            private val myResolvingContext: PackagingElementResolvingContext) : PackagingModel {
    private val myDependenciesImporter: ArtifactExternalDependenciesImporter = ArtifactExternalDependenciesImporterImpl()

    override fun getModifiableArtifactModel(): ModifiableArtifactModel {
      return myArtifactModel
    }

    override fun getPackagingElementResolvingContext(): PackagingElementResolvingContext {
      return myResolvingContext
    }

    override fun getArtifactExternalDependenciesImporter(): ArtifactExternalDependenciesImporter {
      return myDependenciesImporter
    }
  }
}