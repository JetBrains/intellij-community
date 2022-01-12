// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootPropertiesHelper
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenFoldersImporter
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.MavenModuleImporter
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.project.SupportedRequestType
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import java.io.File

class WorkspaceModuleImporter(
  private val mavenProject: MavenProject,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val projectsTree: MavenProjectsTree,
  private val builder: WorkspaceEntityStorageBuilder,
  private val importingSettings: MavenImportingSettings,
  private val mavenProjectToModuleName: HashMap<MavenProject, String>,
  private val project: Project) {

  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID)

  fun importModule(): ModuleEntity {
    val baseModuleDirPath = importingSettings.dedicatedModuleDir.ifBlank { null } ?: mavenProject.directory
    val entitySource = JpsEntitySourceFactory.createEntitySourceForModule(project, virtualFileUrlManager.fromPath(baseModuleDirPath), externalSource)
    val dependencies = collectDependencies(entitySource)
    val moduleName = mavenProjectToModuleName.getValue(mavenProject)
    val moduleEntity = builder.addModuleEntity(moduleName, dependencies, entitySource, ModuleTypeId.JAVA_MODULE)
    builder.addEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, entitySource) {
      module = moduleEntity
      externalSystem = externalSource.id
    }

    val excludedFolders = LinkedHashSet<VirtualFileUrl>()
    importJavaSettings(moduleEntity, excludedFolders)
    importExcludedFolders(excludedFolders)

    val contentRootEntity = builder.addContentRootEntity(virtualFileUrlManager.fromPath(mavenProject.directory), excludedFolders.toList(), emptyList(),
                                                         moduleEntity)
    importSourceFolders(contentRootEntity)
    return moduleEntity
  }

  private fun importExcludedFolders(excludedFolders: MutableCollection<VirtualFileUrl>) {
    val targetDir = File(toAbsolutePath(mavenProject.buildDirectory))
    if (importingSettings.isExcludeTargetFolder) {
      excludedFolders.add(virtualFileUrlManager.fromPath(targetDir.path))
    }
  }


  private fun importJavaSettings(moduleEntity: ModuleEntity, excludedUrls: MutableCollection<VirtualFileUrl>) {
    val languageLevel = MavenModuleImporter.getLanguageLevel(mavenProject)
    val inheritCompilerOutput: Boolean
    val compilerOutputUrl: VirtualFileUrl?
    val compilerOutputUrlForTests: VirtualFileUrl?
    val outputPath = toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject.testOutputDirectory)
    if (importingSettings.isUseMavenOutput) {
      inheritCompilerOutput = false
      compilerOutputUrl = virtualFileUrlManager.fromPath(outputPath)
      compilerOutputUrlForTests = virtualFileUrlManager.fromPath(testOutputPath)
    }
    else {
      inheritCompilerOutput = true
      compilerOutputUrl = null
      compilerOutputUrlForTests = null
    }
    val excludeOutput = false
    builder.addJavaModuleSettingsEntity(inheritCompilerOutput, excludeOutput, compilerOutputUrl, compilerOutputUrlForTests,
                                        languageLevel.name, moduleEntity, moduleEntity.entitySource)
    val buildDirPath = toAbsolutePath(mavenProject.buildDirectory)
    if (!FileUtil.isAncestor(buildDirPath, outputPath, false)) {
      excludedUrls.add(virtualFileUrlManager.fromPath(outputPath))
    }
    if (!FileUtil.isAncestor(buildDirPath, testOutputPath, false)) {
      excludedUrls.add(virtualFileUrlManager.fromPath(testOutputPath))
    }
  }

  private fun collectDependencies(moduleEntitySource: EntitySource): List<ModuleDependencyItem> {
    val dependencyTypes = importingSettings.dependencyTypesAsSet
    dependencyTypes.addAll(mavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT))
    return listOf(ModuleDependencyItem.InheritedSdkDependency,
                  ModuleDependencyItem.ModuleSourceDependency) +
           mavenProject.dependencies.filter { dependencyTypes.contains(it.type) }.mapNotNull { createDependency(it, moduleEntitySource) }

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

  private fun importSourceFolders(contentRootEntity: ContentRootEntity) {
    MavenFoldersImporter.getSourceFolders(mavenProject).forEach { entry ->
      val path = entry.key
      if (!File(path).exists()) return@forEach

      val serializer = (JpsModelSerializerExtension.getExtensions()
        .flatMap { it.moduleSourceRootPropertiesSerializers }
        .firstOrNull { it.type == entry.value })
                       ?: error("Module source root type ${entry}.value is not registered as JpsModelSerializerExtension")

      val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity,
                                                         virtualFileUrlManager.fromUrl(VfsUtilCore.pathToUrl(path)),
                                                         serializer.typeId,
                                                         contentRootEntity.entitySource)
      when (entry.value) {
        is JavaSourceRootType -> builder.addJavaSourceRootEntity(sourceRootEntity, false, "")
        is JavaResourceRootType -> builder.addJavaResourceRootEntity(sourceRootEntity, false, "")
        else -> TODO()
      }
    }

    importGeneratedSourceFolders(contentRootEntity)
  }

  private fun importGeneratedSourceFolders(contentRootEntity: ContentRootEntity) {
    val targetDir = File(mavenProject.buildDirectory)

    val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
    val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)

    if (importingSettings.generatedSourcesFolder != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      addGeneratedJavaSourceFolder(mavenProject.getAnnotationProcessorDirectory(true), JavaSourceRootType.TEST_SOURCE, contentRootEntity)
      addGeneratedJavaSourceFolder(mavenProject.getAnnotationProcessorDirectory(false), JavaSourceRootType.SOURCE, contentRootEntity)
    }

    val targetChildren = targetDir.listFiles()
    if (targetChildren != null) {
      for (f in targetChildren) {
        if (!f.isDirectory) continue
        if (FileUtil.pathsEqual(generatedDir, f.path)) {
          configGeneratedSourceFolder(f, JavaSourceRootType.SOURCE, contentRootEntity)
        }
        else if (FileUtil.pathsEqual(generatedDirTest, f.path)) {
          configGeneratedSourceFolder(f, JavaSourceRootType.TEST_SOURCE, contentRootEntity)
        }
      }
    }
  }

  private fun addGeneratedJavaSourceFolder(path: String, type: JavaSourceRootType, contentRootEntity: ContentRootEntity) {
    if (File(path).list().isNullOrEmpty()) return

    val url = virtualFileUrlManager.fromPath(path)
    if (contentRootEntity.sourceRoots.any { it.url == url }) return

    val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity, url,
                                                       SourceRootPropertiesHelper.findSerializer(type)?.typeId!!,
                                                       contentRootEntity.entitySource)
    builder.addJavaSourceRootEntity(sourceRootEntity, true, "")
  }

  private fun configGeneratedSourceFolder(targetDir: File, rootType: JavaSourceRootType, contentRootEntity: ContentRootEntity) {
    when (importingSettings.generatedSourcesFolder) {
      GeneratedSourcesFolder.GENERATED_SOURCE_FOLDER -> addGeneratedJavaSourceFolder(targetDir.path, rootType, contentRootEntity)
      GeneratedSourcesFolder.SUBFOLDER -> addAllSubDirsAsGeneratedSources(targetDir, rootType, contentRootEntity)
      GeneratedSourcesFolder.AUTODETECT -> {
        val sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir)
        for (root in sourceRoots) {
          if (FileUtil.filesEqual(targetDir, root.directory)) {
            addGeneratedJavaSourceFolder(targetDir.path, rootType, contentRootEntity)
            return
          }
          addAsGeneratedSourceFolder(root.directory, rootType, contentRootEntity)
        }
        addAllSubDirsAsGeneratedSources(targetDir, rootType, contentRootEntity)
      }
      GeneratedSourcesFolder.IGNORE -> {}
    }
  }

  private fun addAsGeneratedSourceFolder(dir: File, rootType: JavaSourceRootType, contentRootEntity: ContentRootEntity) {
    val url = VfsUtilCore.fileToUrl(dir)
    val folder = contentRootEntity.sourceRoots.find { it.url.url == url }
    val hasRegisteredSubfolder = contentRootEntity.sourceRoots.any { VfsUtilCore.isEqualOrAncestor(url, it.url.url) }
    if (!hasRegisteredSubfolder
        || folder != null && (folder.asJavaSourceRoot()?.generated == true || folder.asJavaResourceRoot()?.generated == true)) {
      addGeneratedJavaSourceFolder(dir.path, rootType, contentRootEntity)
    }
  }

  private fun addAllSubDirsAsGeneratedSources(dir: File, rootType: JavaSourceRootType, contentRootEntity: ContentRootEntity) {
    dir.listFiles()?.forEach { f ->
      if (f.isDirectory) {
        addAsGeneratedSourceFolder(f, rootType, contentRootEntity)
      }
    }
  }


  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }

  private fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

  companion object {
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")
  }
}