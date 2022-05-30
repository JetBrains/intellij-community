// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.importing.tree.MavenJavaVersionHolder
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.ModuleData
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.SupportedRequestType

class WorkspaceModuleImporter(
  private val mavenProject: MavenProject,
  virtualFileUrlManager: VirtualFileUrlManager,
  private val projectsTree: MavenProjectsTree,
  builder: MutableEntityStorage,
  importingSettings: MavenImportingSettings,
  private val mavenProjectToModuleName: HashMap<MavenProject, String>,
  project: Project) : WorkspaceModuleImporterBase<MavenModuleImportData>(project, virtualFileUrlManager, builder, importingSettings) {

  fun importModule(): ModuleEntity {
    val moduleData = ModuleData(mavenProjectToModuleName.getValue(mavenProject),
                                MavenModuleType.MAIN_TEST,
                                MavenJavaVersionHolder(null, null))
    val importData = MavenModuleImportData(mavenProject, moduleData)

    return importModule(importData, mutableMapOf())
  }

  override fun collectDependencies(importData: MavenModuleImportData,
                                   entitySource: EntitySource): List<ModuleDependencyItem> {
    val dependencyTypes = importingSettings.dependencyTypesAsSet
    dependencyTypes.addAll(importData.mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT))
    return listOf(ModuleDependencyItem.InheritedSdkDependency,
                  ModuleDependencyItem.ModuleSourceDependency) +
           importData.mavenProject.dependencies.filter { dependencyTypes.contains(it.type) }.mapNotNull {
             createDependency(it, entitySource)
           }

  }

  private fun createDependency(artifact: MavenArtifact, moduleEntitySource: EntitySource): ModuleDependencyItem? {
    val depProject = projectsTree.findProject(artifact.mavenId)
    if (depProject == null) {
      if (artifact.scope == "system") {
        return createSystemDependency(artifact, moduleEntitySource)
      }
      if (artifact.type == "bundle") {
        return addBundleDependency(artifact)
      }
      return createLibraryDependency(artifact)
    }
    if (depProject === mavenProject) {
      return null
    }
    val depModuleName = mavenProjectToModuleName[depProject]
    if (depModuleName == null || projectsTree.isIgnored(depProject)) {
      return createLibraryDependency(MavenModuleImporter.createCopyForLocalRepo(artifact, mavenProject))
    }
    return createModuleDependency(artifact, depModuleName)
  }

  private fun addBundleDependency(artifact: MavenArtifact): ModuleDependencyItem {
    val newArtifact = MavenArtifact(
      artifact.groupId,
      artifact.artifactId,
      artifact.version,
      artifact.baseVersion,
      "jar",
      artifact.classifier,
      artifact.scope,
      artifact.isOptional,
      "jar",
      null,
      mavenProject.localRepository,
      false, false
    )
    return createLibraryDependency(newArtifact)
  }

  private fun createSystemDependency(artifact: MavenArtifact, moduleEntitySource: EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId.COMPILED))

    val libraryTableId = LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(mavenProject.displayName)) //(ModuleId(moduleEntity.name))


    builder.addLibraryEntity(artifact.libraryName, libraryTableId,
                             roots,
                             emptyList(), moduleEntitySource)

    return ModuleDependencyItem.Exportable.LibraryDependency(LibraryId(artifact.libraryName, libraryTableId), false,
                                                             artifact.dependencyScope)
  }

  private fun createModuleDependency(artifact: MavenArtifact, moduleName: String): ModuleDependencyItem {
    val isTestJar = MavenConstants.TYPE_TEST_JAR == artifact.type || "tests" == artifact.classifier
    return ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(moduleName), false, artifact.dependencyScope, isTestJar)
  }

  private fun createLibraryDependency(artifact: MavenArtifact): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM != artifact.scope)
    val libraryId = LibraryId(artifact.libraryName, LibraryTableId.ProjectLibraryTableId)
    if (builder.resolve(libraryId) == null) {
      addLibraryToProjectTable(artifact)
    }

    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, artifact.dependencyScope)
  }

  private fun addLibraryToProjectTable(artifact: MavenArtifact): LibraryEntity {
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId.COMPILED))
    roots.add(
      LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")),
                  JAVADOC_TYPE))
    roots.add(
      LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")),
                  LibraryRootTypeId.SOURCES))

    val libraryTableId = LibraryTableId.ProjectLibraryTableId //(ModuleId(moduleEntity.name))

    return builder.addLibraryEntity(artifact.libraryName, libraryTableId,
                                    roots,
                                    emptyList(), JpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource))
  }

  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }
}