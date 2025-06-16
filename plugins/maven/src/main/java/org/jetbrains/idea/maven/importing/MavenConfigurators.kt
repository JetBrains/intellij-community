// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildByPath
import org.jetbrains.idea.maven.utils.MavenJDOMUtil.findChildValueByPath
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.stream.Stream

@ApiStatus.Experimental
@Suppress("DEPRECATION")
interface MavenWorkspaceConfigurator {
  companion object {
    @JvmField
    val EXTENSION_POINT_NAME: ExtensionPointName<MavenWorkspaceConfigurator> = ExtensionPointName("org.jetbrains.idea.maven.importing.workspaceConfigurator")
  }

  /**
   * Called for each imported project in order to add
   * [com.intellij.platform.workspace.storage.bridgeEntities.SourceRootEntity]-es to the corresponding [ModuleEntity]-es.
   *
   * * Called on a background thread.
   * * Side-effects are not allowed.
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun getAdditionalFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<AdditionalFolder> {
    return Stream.empty()
  }


  /**
   * Called for each imported project.
   * Implement this method to prevent creation of [com.intellij.platform.workspace.storage.bridgeEntities.SourceRootEntity]-es in the corresponding [ModuleEntity]-es.
   * These folders are also marked as 'excluded' in the corresponding [Module]. See [com.intellij.openapi.roots.ExcludeFolder].
   *
   *
   * * Called on a background thread.
   * * Side-effects are not allowed.
   * * WriteActions are not allowed.
   */
  @RequiresBackgroundThread
  fun getFoldersToExclude(context: FoldersContext): Stream<String> {
    return Stream.empty()
  }

  interface FoldersContext {
    val mavenProject: MavenProject
  }

  /**
   * Called for each imported project. Order of projects is not defined.
   * Corresponding [ModuleEntity] is already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel].
   * Other Modules are not accessible.
   *
   * * Called on a background thread.
   * * WriteActions are not allowed.
   * * Side-effects other than changing [ModuleEntity]-related entities are not allowed.
   */
  @RequiresBackgroundThread
  fun configureMavenProject(context: MutableMavenProjectContext) {
  }

  /**
   * Called once per import, after [configureMavenProject] has been called for all [MavenWorkspaceConfigurator]s and all [Module]s.
   * [ModuleEntity]-es are already created and filled with folders and dependencies,
   * but not yet applied to [com.intellij.workspaceModel.ide.WorkspaceModel].
   *
   * * Called on a background thread.
   * * WriteActions are not allowed.
   * * Side-effects other than changing [MutableEntityStorage] are not allowed
   */
  @RequiresBackgroundThread
  fun beforeModelApplied(context: MutableModelContext) {
  }

  /**
   * Called once per import, after all the changes are applied [com.intellij.workspaceModel.ide.WorkspaceModel].
   * Implement this method in order to make modifications in components, which are not represented in the [com.intellij.workspaceModel.ide.WorkspaceModel],
   * but which should be kept in sync with it.
   *
   * For slower/non-immediate configuration use [MavenAfterImportConfigurator]
   *
   * * Called in WriteAction.
   * * Should be as fast as possible.
   * * Necessary preparations must be done in [beforeModelApplied] or [configureMavenProject]. Data can be passed context as [UserDataHolder].
   */
  @RequiresWriteLock
  fun afterModelApplied(context: AppliedModelContext) {
  }

  interface ModuleWithType<M> {
    val module: M
    val type: MavenModuleType
  }

  /**
   * Every Maven project is represented by one or several IJ [Module]s. See [org.jetbrains.idea.maven.importing.StandardMavenModuleType] for the list of possible module types.
   * Configuration implementation should be careful when configuring each [Module] and distinguish between modules with code, pure main or test modules.
   *
   */
  interface MavenProjectWithModules<M> {
    val mavenProject: MavenProject
    val hasChanges: Boolean
    val modules: List<ModuleWithType<M>>
  }

  interface Context<S : EntityStorage> : UserDataHolderEx {
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

  enum class FolderType {
    SOURCE, RESOURCE, TEST_SOURCE, TEST_RESOURCE, GENERATED_SOURCE, GENERATED_TEST_SOURCE
  }

  fun JpsModuleSourceRootType<*>.toFolderType(): FolderType {
    return when (this) {
      is JavaSourceRootType -> if (isForTests) FolderType.TEST_SOURCE else FolderType.SOURCE
      is JavaResourceRootType -> if (isForTests) FolderType.TEST_RESOURCE else FolderType.RESOURCE
      else -> throw IllegalArgumentException("Bad Module source root type")
    }
  }

  data class AdditionalFolder(val path: String, val type: FolderType)
}

@ApiStatus.Experimental
interface MavenAfterImportConfigurator {
  /**
   * Called in background after project import is finished.
   * See also [org.jetbrains.idea.maven.project.MavenImportListener]
   */
  fun afterImport(context: Context) {
  }

  interface Context : UserDataHolder {
    val project: Project
    val mavenProjectsWithModules: Sequence<MavenWorkspaceConfigurator.MavenProjectWithModules<Module>>
    val progressIndicator: ProgressIndicator
  }
}

@ApiStatus.Internal
open class MavenApplicableConfigurator(private val pluginGroupId: String, private val pluginArtifactId: String) {
  open fun isApplicable(mavenProject: MavenProject): Boolean {
    return mavenProject.findPlugin(pluginGroupId, pluginArtifactId) != null
  }

  protected open fun findConfigValue(p: MavenProject, @NonNls path: String): String? {
    return findChildValueByPath(getConfig(p), path)
  }

  protected open fun getConfig(p: MavenProject): Element? {
    return p.getPluginConfiguration(pluginGroupId, pluginArtifactId)
  }

  protected open fun getConfig(p: MavenProject, @NonNls path: String): Element? {
    return findChildByPath(getConfig(p), path)
  }
}

fun <M> MavenWorkspaceConfigurator.MavenProjectWithModules<M>.hasChanges(): Boolean {
  return this.hasChanges
}


@ApiStatus.Experimental
interface MavenStaticSyncAware