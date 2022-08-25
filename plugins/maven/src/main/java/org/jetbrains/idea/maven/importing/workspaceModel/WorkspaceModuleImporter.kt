// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.jps.serialization.FileInDirectorySourceNames
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenImportUtil
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenLog

internal class WorkspaceModuleImporter(
  private val project: Project,
  private val importData: MavenTreeModuleImportData,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val builder: MutableEntityStorage,
  private val importingSettings: MavenImportingSettings,
  private val folderImportingContext: WorkspaceFolderImporter.FolderImportingContext,
  private val stats: WorkspaceImportStats
) {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(EXTERNAL_SOURCE_ID)

  fun importModule(): ModuleEntity {
    val baseModuleDirPath = importingSettings.dedicatedModuleDir.ifBlank { null } ?: importData.mavenProject.directory
    val baseModuleDir = virtualFileUrlManager.fromPath(baseModuleDirPath)

    val moduleName = importData.moduleData.moduleName
    val sourceNames = FileInDirectorySourceNames.from(WorkspaceModel.getInstance(project).entityStorage.current)

    val moduleLibrarySource = createModuleLibrarySource(sourceNames, moduleName, baseModuleDir)

    val dependencies = collectDependencies(moduleName, importData.dependencies, moduleLibrarySource, sourceNames)
    val moduleEntity = createModuleEntity(moduleName, importData.mavenProject, importData.moduleData.type, dependencies,
                                          moduleLibrarySource)
    configureModuleEntity(importData, moduleEntity, folderImportingContext)
    return moduleEntity
  }

  private fun createModuleLibrarySource(sourceNames: FileInDirectorySourceNames,
                                        moduleName: String,
                                        baseModuleDir: VirtualFileUrl): EntitySource {
    val internalSource = sourceNames.findSource(ModuleEntity::class.java, moduleName + ModuleManagerEx.IML_EXTENSION)
                         ?: JpsEntitySourceFactory.createInternalEntitySourceForModule(project, baseModuleDir)
    return JpsEntitySourceFactory.createImportedEntitySource(project, externalSource, internalSource)
  }

  private fun createProjectLibrarySource(sourceNames: FileInDirectorySourceNames, libraryName: String): EntitySource {
    val internalSource = sourceNames.findSource(LibraryEntity::class.java, libraryName)
                         ?: JpsEntitySourceFactory.createInternalEntitySourceForProjectLibrary(project)
    return JpsEntitySourceFactory.createImportedEntitySource(project, externalSource, internalSource)
  }

  private fun createModuleEntity(moduleName: String,
                                 mavenProject: MavenProject,
                                 mavenModuleType: StandardMavenModuleType,
                                 dependencies: List<ModuleDependencyItem>,
                                 entitySource: EntitySource): ModuleEntity {
    val moduleEntity = builder.addModuleEntity(moduleName, dependencies, entitySource, ModuleTypeId.JAVA_MODULE)
    val externalSystemModuleOptionsEntity = ExternalSystemModuleOptionsEntity(entitySource) {
      ExternalSystemData(moduleEntity, mavenProject.file.path, mavenModuleType).write(this)
    }
    builder.addEntity(externalSystemModuleOptionsEntity)
    return moduleEntity

  }

  private fun configureModuleEntity(importData: MavenModuleImportData,
                                    moduleEntity: ModuleEntity,
                                    folderImportingContext: WorkspaceFolderImporter.FolderImportingContext) {
    val folderImporter = WorkspaceFolderImporter(builder, virtualFileUrlManager, importingSettings, folderImportingContext)
    val importFolderHolder = folderImporter.createContentRoots(importData.mavenProject, importData.moduleData.type, moduleEntity,
                                                               stats)

    importJavaSettings(moduleEntity, importData, importFolderHolder)
  }

  private fun collectDependencies(moduleName: String,
                                  dependencies: List<Any>,
                                  moduleLibrarySource: EntitySource,
                                  sourceNames: FileInDirectorySourceNames): List<ModuleDependencyItem> {
    val result = mutableListOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
    for (dependency in dependencies) {
      if (dependency is SystemDependency) {
        result.add(createSystemDependency(moduleName, dependency.artifact, moduleLibrarySource))
      }
      else if (dependency is LibraryDependency) {
        val source = createProjectLibrarySource(sourceNames, dependency.artifact.libraryName)
        result.add(createLibraryDependency(dependency.artifact, source))
      }
      else if (dependency is AttachedJarDependency) {
        val source = createProjectLibrarySource(sourceNames, dependency.artifact)
        result.add(createLibraryDependency(
          dependency.artifact,
          toScope(dependency.scope),
          { dependency.rootPaths.map { (url, type) -> LibraryRoot(virtualFileUrlManager.fromUrl(pathToUrl(url)), type) } },
          source
        ))
      }
      else if (dependency is ModuleDependency) {
        result.add(ModuleDependencyItem.Exportable
                     .ModuleDependency(ModuleId(dependency.artifact), false, toScope(dependency.scope), dependency.isTestJar))
      }
      else if (dependency is BaseDependency) {
        val source = createProjectLibrarySource(sourceNames, dependency.artifact.libraryName)
        result.add(createLibraryDependency(dependency.artifact, source))
      }
    }
    return result
  }

  private fun pathToUrl(it: String) = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, it) + JarFileSystem.JAR_SEPARATOR

  private fun toScope(scope: DependencyScope): ModuleDependencyItem.DependencyScope =
    when (scope) {
      DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
      DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }


  private fun createSystemDependency(moduleName: String,
                                     artifact: MavenArtifact,
                                     entitySource: EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)

    val libraryId = LibraryId(artifact.libraryName, LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName)))
    addLibraryEntity(libraryId,
                     {
                       val classes = MavenImportUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)
                       listOf(LibraryRoot(virtualFileUrlManager.fromUrl(classes), LibraryRootTypeId.COMPILED))
                     },
                     entitySource)
    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, artifact.dependencyScope)
  }

  private fun createLibraryDependency(artifact: MavenArtifact,
                                      entitySource: EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM != artifact.scope)
    val libraryRootsProvider = {
      val classes = MavenImportUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)
      val sources = MavenImportUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")
      val javadoc = MavenImportUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")
      listOf(
        LibraryRoot(virtualFileUrlManager.fromUrl(classes), LibraryRootTypeId.COMPILED),
        LibraryRoot(virtualFileUrlManager.fromUrl(sources), LibraryRootTypeId.SOURCES),
        LibraryRoot(virtualFileUrlManager.fromUrl(javadoc), JAVADOC_TYPE),
      )
    }
    return createLibraryDependency(artifact.libraryName,
                                   artifact.dependencyScope,
                                   libraryRootsProvider,
                                   entitySource)
  }

  private fun createLibraryDependency(
    libraryName: String,
    scope: ModuleDependencyItem.DependencyScope,
    libraryRootsProvider: () -> List<LibraryRoot>,
    source: EntitySource
  ): ModuleDependencyItem.Exportable.LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)

    addLibraryEntity(libraryId, libraryRootsProvider, source)

    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, scope)
  }

  private fun addLibraryEntity(
    libraryId: LibraryId,
    libraryRootsProvider: () -> List<LibraryRoot>, // lazy provider to avoid roots creation for already added libraries
    source: EntitySource) {
    if (libraryId in builder) return

    builder.addLibraryEntity(libraryId.name,
                             libraryId.tableId,
                             libraryRootsProvider.invoke(),
                             emptyList(),
                             source)
  }

  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }


  private fun importJavaSettings(moduleEntity: ModuleEntity,
                                 importData: MavenModuleImportData,
                                 importFolderHolder: WorkspaceFolderImporter.CachedProjectFolders) {
    val languageLevel = MavenImportUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }

    var inheritCompilerOutput = true
    var compilerOutputUrl: VirtualFileUrl? = null
    var compilerOutputUrlForTests: VirtualFileUrl? = null

    val moduleType = importData.moduleData.type

    if (moduleType.containsCode && importingSettings.isUseMavenOutput) {

      inheritCompilerOutput = false
      if (moduleType.containsMain) {
        compilerOutputUrl = virtualFileUrlManager.fromPath(importFolderHolder.outputPath)
      }
      if (moduleType.containsTest) {
        compilerOutputUrlForTests = virtualFileUrlManager.fromPath(importFolderHolder.testOutputPath)
      }
    }
    builder.addJavaModuleSettingsEntity(inheritCompilerOutput, false, compilerOutputUrl, compilerOutputUrlForTests,
                                        languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  companion object {
    val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")

    val EXTERNAL_SOURCE_ID get() = ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID
  }

  class ExternalSystemData(val moduleEntity: ModuleEntity, val mavenProjectFilePath: String, val mavenModuleType: StandardMavenModuleType) {
    fun write(entity: ExternalSystemModuleOptionsEntity.Builder) {
      entity.externalSystemModuleVersion = VERSION
      entity.module = moduleEntity
      entity.externalSystem = EXTERNAL_SOURCE_ID
      // Can't use 'entity.linkedProjectPath' since it implies directory (and used to set working dir for Run Configurations).
      entity.linkedProjectId = FileUtil.toSystemIndependentName(mavenProjectFilePath)
      entity.externalSystemModuleType = mavenModuleType.name
    }

    companion object {
      const val VERSION = "223-2"

      fun tryRead(entity: ExternalSystemModuleOptionsEntity): ExternalSystemData? {
        if (entity.externalSystem != EXTERNAL_SOURCE_ID || entity.externalSystemModuleVersion != VERSION) return null

        val id = entity.linkedProjectId
        if (id == null) {
          MavenLog.LOG.debug("ExternalSystemModuleOptionsEntity.linkedProjectId must not be null")
          return null
        }
        val mavenProjectFilePath = FileUtil.toSystemIndependentName(id)

        val typeName = entity.externalSystemModuleType
        if (typeName == null) {
          MavenLog.LOG.debug("ExternalSystemModuleOptionsEntity.externalSystemModuleType must not be null")
          return null
        }

        val moduleType = try {
          StandardMavenModuleType.valueOf(typeName)
        }
        catch (e: Exception) {
          MavenLog.LOG.debug(e)
          return null
        }
        return ExternalSystemData(entity.module, mavenProjectFilePath, moduleType)
      }
    }
  }
}