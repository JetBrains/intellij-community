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
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsTree

@ApiStatus.Experimental
interface MavenConfigurator {
  companion object {
    val EXTENSION_POINT_NAME: ExtensionPointName<MavenConfigurator> = ExtensionPointName.create("org.jetbrains.idea.maven.configurator")
  }

  /**
   * Called for each imported project. Order of projects is not defined.
   * [ModuleEntity]-es are already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel].
   *
   * * Called on a background thread.
   * * Implementations are responsible for taking readActions (preferably cancelable).
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun configureMavenProject(context: MutableMavenProjectContext) {
  }

  /**
   * Called once per import, after [configureMavenProject] has been called for all [MavenConfigurator]s and all [Module]s.
   * [ModuleEntity]-es are already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel].
   *
   * * Called on a background thread.
   * * Implementations are responsible for taking readActions (preferably cancelable).
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun beforeModelApplied(context: MutableModelContext) {
  }

  /**
   * Called once per import, after all the changes are applied to applied to [com.intellij.workspaceModel.ide.WorkspaceModel]
   *
   * * Called in WriteAction.
   * * Should be as fast as possible.
   * * Necessary preparations must be done in [beforeModelApplied] or [configureMavenProject]. Data can be passed context as [UserDataHolder]
   */
  @RequiresWriteLock
  fun afterModelApplied(context: AppliedModelContext) {
  }

  interface ModuleWithType<M> {
    val module: M
    val type: MavenModuleType
  }

  /**
   * Every Maven project is represented by one or several IJ [Module]s. See [org.jetbrains.idea.maven.importing.MavenModuleType] for the list of possible module types.
   * Configuration implementation should be careful when configuring each [Module], e.g. [org.jetbrains.idea.maven.importing.MavenModuleType.TEST_ONLY] should be configured for test sources only.
   *
   */
  interface MavenProjectWithModules<M> {
    val mavenProject: MavenProject
    val changes: MavenProjectChanges
    val modules: List<ModuleWithType<M>>
  }

  interface Context<S : EntityStorage> : UserDataHolder {
    val project: Project
    val storage: S
    val mavenProjectsTree: MavenProjectsTree
  }

  interface ModelContext<S : EntityStorage, M> : Context<S> {
    val mavenProjectsWithModules: Sequence<MavenProjectWithModules<M>>
    fun <T : WorkspaceEntity> importedEntities(clazz: Class<T>): Sequence<T>
  }

  interface MutableModelContext : ModelContext<MutableEntityStorage, ModuleEntity>
  interface AppliedModelContext : ModelContext<EntityStorage, Module>

  interface MutableMavenProjectContext : Context<MutableEntityStorage> {
    val mavenProjectWithModules: MavenProjectWithModules<ModuleEntity>
  }
}