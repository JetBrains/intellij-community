// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.worktree

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.idea.maven.importing.MavenFoldersImporter
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.SupportedRequestType
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer

class WorkspaceModuleImporter(private val project: Project,
                              private val mavenProject: MavenProject,
                              private val projectsTree: MavenProjectsTree,
                              private val diff: WorkspaceEntityStorageBuilder) {

  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private lateinit var moduleEntity: ModuleEntity
  fun importModule() {
    val dependencies = collectDependencies();
    moduleEntity = diff.addModuleEntity(mavenProject.displayName, dependencies, MavenExternalSource.INSTANCE)
    val contentRootEntity = diff.addContentRootEntity(virtualFileManager.fromPath(mavenProject.directory), emptyList(), emptyList(),
                                                      moduleEntity,
                                                      MavenExternalSource.INSTANCE)
    importFolders(contentRootEntity)
    importLanguageLevel();
  }


  private fun importLanguageLevel() {

  }

  private fun collectDependencies(): List<ModuleDependencyItem> {
    val dependencyTypes = MavenProjectsManager.getInstance(project).importingSettings.dependencyTypesAsSet;
    dependencyTypes.addAll(mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT))
    return listOf(ModuleDependencyItem.ModuleSourceDependency,
                  ModuleDependencyItem.InheritedSdkDependency) +
           mavenProject.dependencies.filter { dependencyTypes.contains(it.type) }.mapNotNull(this::createDependency)

  }

  private fun createDependency(artifact: MavenArtifact): ModuleDependencyItem? {
    val depProject = projectsTree.findProject(artifact.mavenId)
    if (depProject == null) {
      if (artifact.scope == "system") {
        return createSystemDependency(artifact)
      }
      if (artifact.type == "bundle") {
        return addBundleDependency(artifact)
      }
      return createLibraryDependency(artifact)
    }
    if (depProject === mavenProject) {
      return null
    }
    if (projectsTree.isIgnored(depProject)) {
      TODO()
    }
    return createModuleDependency(artifact, depProject)
  }

  private fun addBundleDependency(artifact: MavenArtifact): ModuleDependencyItem? {
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
      mavenProject.getLocalRepository(),
      false, false
    );
    return createLibraryDependency(newArtifact);
  }

  private fun createSystemDependency(artifact: MavenArtifact): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId("CLASSES"),
                          LibraryRoot.InclusionOptions.ROOT_ITSELF))

    val libraryTableId = LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(mavenProject.displayName)); //(ModuleId(moduleEntity.name))


    diff.addLibraryEntity(artifact.libraryName, libraryTableId,
                          roots,
                          emptyList(), MavenExternalSource.INSTANCE)

    return ModuleDependencyItem.Exportable.LibraryDependency(LibraryId(artifact.libraryName, libraryTableId), false,
                                                             toEntityScope(artifact.scope))
  }

  private fun createModuleDependency(artifact: MavenArtifact, depProject: MavenProject): ModuleDependencyItem {
    val isTestJar = MavenConstants.TYPE_TEST_JAR == artifact.type || "tests" == artifact.classifier
    return ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(depProject.displayName), false,
                                                            toEntityScope(artifact.scope), isTestJar)
  }

  private fun createLibraryDependency(artifact: MavenArtifact): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM != artifact.scope)
    if (!libraryExists(artifact)) {
      addLibraryToProjectTable(artifact)
    }
    val libraryTableId = LibraryTableId.ProjectLibraryTableId; //(ModuleId(moduleEntity.name))

    return ModuleDependencyItem.Exportable.LibraryDependency(LibraryId(artifact.libraryName, libraryTableId), false,
                                                             toEntityScope(artifact.scope))
  }

  private fun libraryExists(artifact: MavenArtifact): Boolean {
    return diff.entities(LibraryEntity::class.java).any { it.name == artifact.libraryName }
  }

  private fun addLibraryToProjectTable(artifact: MavenArtifact): LibraryEntity {
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId("CLASSES"),
                          LibraryRoot.InclusionOptions.ROOT_ITSELF))
    roots.add(
      LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")),
                  LibraryRootTypeId("JAVADOC"),
                  LibraryRoot.InclusionOptions.ROOT_ITSELF))
    roots.add(
      LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")),
                  LibraryRootTypeId("SOURCES"),
                  LibraryRoot.InclusionOptions.ROOT_ITSELF))

    val libraryTableId = LibraryTableId.ProjectLibraryTableId; //(ModuleId(moduleEntity.name))

    return diff.addLibraryEntity(artifact.libraryName, libraryTableId,
                                 roots,
                                 emptyList(), MavenExternalSource.INSTANCE)
  }

  private fun importFolders(contentRootEntity: ContentRootEntity) {
    MavenFoldersImporter.getSourceFolders(mavenProject).forEach { entry ->

      val serializer = (JpsModelSerializerExtension.getExtensions()
        .flatMap { it.moduleSourceRootPropertiesSerializers }
        .firstOrNull { it.type == entry.value }) as? JpsModuleSourceRootPropertiesSerializer
                       ?: error("Module source root type ${entry}.value is not registered as JpsModelSerializerExtension")

      val sourceRootEntity = diff.addSourceRootEntity(moduleEntity, contentRootEntity,
                                                      virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(entry.key)),
                                                      entry.value.isForTests,
                                                      serializer.typeId,
                                                      MavenExternalSource.INSTANCE)
      when (entry.value) {
        is JavaSourceRootType -> diff.addJavaSourceRootEntity(sourceRootEntity, false, "", MavenExternalSource.INSTANCE)
        is JavaResourceRootType -> diff.addJavaResourceRootEntity(sourceRootEntity, false, "", MavenExternalSource.INSTANCE)
        else -> TODO()
      }
    }
  }

  fun toEntityScope(mavenScope: String): ModuleDependencyItem.DependencyScope {
    if (MavenConstants.SCOPE_RUNTIME == mavenScope) return ModuleDependencyItem.DependencyScope.RUNTIME
    if (MavenConstants.SCOPE_TEST == mavenScope) return ModuleDependencyItem.DependencyScope.TEST
    return if (MavenConstants.SCOPE_PROVIDED == mavenScope) ModuleDependencyItem.DependencyScope.PROVIDED else ModuleDependencyItem.DependencyScope.COMPILE
  }
}