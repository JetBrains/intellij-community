// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject

@ApiStatus.Experimental
interface MavenConfigurator {
  companion object {
    val EXTENSION_POINT_NAME: ExtensionPointName<MavenConfigurator> = ExtensionPointName.create("org.jetbrains.idea.maven.configurator")
  }

  /**
   * Called for each imported project.
   * [ModuleEntity]-es are already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel]
   *
   * * Called on a background thread.
   * * Implementations are responsible for taking readActions (preferably cancelable).
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun configureModule(context: MutableModuleContext) {}

  /**
   * Called once per import, after [configureModule] has been called for all [MavenConfigurator]s and all [Module]s.
   * [ModuleEntity]-es are already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel].
   *
   * * Called on a background thread.
   * * Implementations are responsible for taking readActions (preferably cancelable).
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun beforeModelApplied(context: MutableContext) {}

  /**
   * Called once per import, after all the changes are applied to applied to [com.intellij.workspaceModel.ide.WorkspaceModel]
   *
   * * Called in WriteAction.
   * * Should be as fast as possible.
   * * Necessary preparations must be done in [beforeModelApplied] or [configureModule]. Data can be passed context as [UserDataHolder]
   */
  @RequiresWriteLock
  fun afterModelApplied(context: AppliedContext) {}

  data class ModuleWithType<M>(val module: M, val type: MavenModuleType)
  data class MavenProjectWithModules<M>(val mavenProject: MavenProject,
                                        val modules: List<ModuleWithType<M>>)

  interface Context<S : EntityStorage, M> : UserDataHolder {
    val project: Project
    val storage: S

    val mavenProjectsWithModules: List<MavenProjectWithModules<M>>

    fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T>
  }

  interface MutableContext : Context<MutableEntityStorage, ModuleEntity>
  interface AppliedContext : Context<EntityStorage, Module>

  interface MutableModuleContext : UserDataHolder {
    val project: Project
    val storage: MutableEntityStorage

    val mavenProjectWithModules: MavenProjectWithModules<ModuleEntity>
  }
}