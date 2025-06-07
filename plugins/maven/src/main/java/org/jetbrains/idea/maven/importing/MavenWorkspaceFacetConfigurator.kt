// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.ConcurrencyUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator.*
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import java.util.concurrent.ConcurrentHashMap

private val FACET_DETECTION_DISABLED_KEY = Key.create<ConcurrentHashMap<MavenWorkspaceFacetConfigurator, Boolean>>("FACET_DETECTION_DISABLED_KEY")

@ApiStatus.Experimental
interface MavenWorkspaceFacetConfigurator : MavenWorkspaceConfigurator {
  fun isApplicable(mavenProject: MavenProject): Boolean
  fun isFacetDetectionDisabled(project: Project): Boolean

  fun preProcess(storage: MutableEntityStorage,
                 module: ModuleEntity,
                 project: Project,
                 mavenProject: MavenProject,
                 userDataHolder: UserDataHolderEx) = preProcess(storage, module, project, mavenProject)

  fun preProcess(storage: MutableEntityStorage, module: ModuleEntity, project: Project, mavenProject: MavenProject) {
  }

  fun process(storage: MutableEntityStorage,
              module: ModuleEntity,
              project: Project,
              mavenProject: MavenProject,
              mavenTree: MavenProjectsTree,
              mavenProjectToModuleName: Map<MavenProject, String>,
              userDataHolder: UserDataHolderEx) = process(storage, module, project, mavenProject)

  fun process(storage: MutableEntityStorage,
              module: ModuleEntity,
              project: Project,
              mavenProject: MavenProject) {
  }

  private fun isFacetDetectionDisabled(context: Context<*>): Boolean {
    return ConcurrencyUtil.computeIfAbsent(context, FACET_DETECTION_DISABLED_KEY, { ConcurrentHashMap()}).computeIfAbsent(this) { isFacetDetectionDisabled(context.project) }
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
        preProcess(context.storage, module, project, mavenProject, context)
      }
    }
  }

  override fun beforeModelApplied(context: MutableModelContext) {
    if (isFacetDetectionDisabled(context)) return

    val project = context.project
    val mavenProjectsWithModules = context.mavenProjectsWithModules
    val storage = context.storage
    val mavenTree = context.mavenProjectsTree

    fun mavenModuleTypeOrder(type: MavenModuleType): Int = when (type) {
      StandardMavenModuleType.SINGLE_MODULE -> 0
      StandardMavenModuleType.MAIN_ONLY -> 1
      StandardMavenModuleType.MAIN_ONLY_ADDITIONAL -> 2
      StandardMavenModuleType.TEST_ONLY -> 3
      StandardMavenModuleType.COMPOUND_MODULE -> 4
      StandardMavenModuleType.AGGREGATOR -> 5
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
                  context)
        }
      }
    }
  }
}