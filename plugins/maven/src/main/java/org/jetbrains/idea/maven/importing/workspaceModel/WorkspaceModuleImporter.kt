// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.configurationStore.serialize
import com.intellij.externalSystem.ImportedLibraryProperties
import com.intellij.externalSystem.ImportedLibraryType
import com.intellij.java.library.MavenCoordinates
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.addIfNotNull
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
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
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class WorkspaceModuleImporter(
  private val project: Project,
  private val storageBeforeImport: EntityStorage,
  private val importData: MavenTreeModuleImportData,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val builder: MutableEntityStorage,
  private val existingEntitySourceNames: FileInDirectorySourceNames,
  private val importingSettings: MavenImportingSettings,
  private val folderImportingContext: WorkspaceFolderImporter.FolderImportingContext,
  private val stats: WorkspaceImportStats
) {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(EXTERNAL_SOURCE_ID)

  fun importModule(): ModuleEntity {
    val baseModuleDir = importData.mavenProject.directoryFile.toVirtualFileUrl(virtualFileUrlManager)
    val moduleName = importData.moduleData.moduleName

    val moduleLibrarySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForModule(project, baseModuleDir, externalSource,
                                                                                             existingEntitySourceNames,
                                                                                 moduleName + ModuleManagerEx.IML_EXTENSION)

    val originalModule = storageBeforeImport.resolve(ModuleId(moduleName))
    val dependencies = collectDependencies(moduleName, originalModule, importData.dependencies, moduleLibrarySource)
    val moduleEntity = createModuleEntity(moduleName, importData.mavenProject, importData.moduleData.type, dependencies,
                                          moduleLibrarySource)
    configureModuleEntity(importData, moduleEntity, folderImportingContext)
    return moduleEntity
  }

  private fun reuseOrCreateProjectLibrarySource(libraryName: String): EntitySource {
    return LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource, existingEntitySourceNames, libraryName)
  }

  private fun createModuleEntity(moduleName: String,
                                 mavenProject: MavenProject,
                                 mavenModuleType: StandardMavenModuleType,
                                 dependencies: List<ModuleDependencyItem>,
                                 entitySource: EntitySource): ModuleEntity {
    val moduleEntity = builder addEntity ModuleEntity(moduleName, dependencies, entitySource) {
      this.type = ModuleTypeId.JAVA_MODULE
    }
    builder addEntity ExternalSystemModuleOptionsEntity(entitySource) {
      ExternalSystemData(moduleEntity, mavenProject.file.path, mavenModuleType).write(this)
    }
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
                                  originalModule: ModuleEntity?,
                                  dependencies: List<MavenImportDependency<*>>,
                                  moduleLibrarySource: EntitySource): List<ModuleDependencyItem> {
    val result = ArrayList<ModuleDependencyItem>(2 + dependencies.size)

    // In this way we keep the manual change of the used JDK
    // If the user changes the default JDK for the module, this information is not stored to maven and is removed after import
    // By checking the state of the module before reimport, we can restore the used JDK
    //
    // With the new workspace model we can make this process working out of the box. For that we need to extract module
    //   dependencies as separate entities and add the SDK dependency with the user defined EntitySource. However, this will require
    //   the refactoring of the ModuleEntity
    val moduleSdk = originalModule?.dependencies?.find { it is ModuleDependencyItem.SdkDependency }
                    ?: ModuleDependencyItem.InheritedSdkDependency
    result.add(moduleSdk)
    result.add(ModuleDependencyItem.ModuleSourceDependency)

    for (dependency in dependencies) {
      val created = when (dependency) {
        is SystemDependency ->
          createSystemDependency(moduleName, dependency.artifact) { moduleLibrarySource }
        is LibraryDependency ->
          createLibraryDependency(dependency.artifact) { reuseOrCreateProjectLibrarySource(dependency.artifact.libraryName) }
        is AttachedJarDependency ->
          createLibraryDependency(dependency.artifact,
                                  toScope(dependency.scope),
                                  null,
                                  {
                                    dependency.rootPaths.map { (url, type) ->
                                      LibraryRoot(virtualFileUrlManager.fromUrl(pathToUrl(url)), type)
                                    }
                                  },
                                  { reuseOrCreateProjectLibrarySource(dependency.artifact) })
        is ModuleDependency ->
          ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(dependency.artifact),
                                                           false,
                                                           toScope(dependency.scope),
                                                           dependency.isTestJar)
        is BaseDependency ->
          createLibraryDependency(dependency.artifact) { reuseOrCreateProjectLibrarySource(dependency.artifact.libraryName) }
        else -> null
      }
      result.addIfNotNull(created)
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
                                     sourceProvider: () -> EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)

    val libraryId = LibraryId(artifact.libraryName, LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName)))
    addLibraryEntity(libraryId,
                     artifact,
                     {
                       val classes = MavenImportUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)
                       listOf(LibraryRoot(virtualFileUrlManager.fromUrl(classes), LibraryRootTypeId.COMPILED))
                     },
                     sourceProvider)
    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, artifact.dependencyScope)
  }

  private fun createLibraryDependency(artifact: MavenArtifact,
                                      sourceProvider: () -> EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
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
                                   artifact,
                                   libraryRootsProvider,
                                   sourceProvider)
  }

  private fun createLibraryDependency(
    libraryName: String,
    scope: ModuleDependencyItem.DependencyScope,
    artifact: MavenArtifact?,
    libraryRootsProvider: () -> List<LibraryRoot>,
    sourceProvider: () -> EntitySource
  ): ModuleDependencyItem.Exportable.LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)

    addLibraryEntity(libraryId, artifact, libraryRootsProvider, sourceProvider)

    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, scope)
  }

  private fun addLibraryEntity(
    libraryId: LibraryId,
    mavenArtifact: MavenArtifact?,
    libraryRootsProvider: () -> List<LibraryRoot>, // lazy provider to avoid roots creation for already added libraries
    sourceProvider: () -> EntitySource) {
    if (libraryId in builder) return

    val source = sourceProvider()
    val libraryEntity = builder addEntity LibraryEntity(libraryId.name, libraryId.tableId, libraryRootsProvider(), source)

    if (mavenArtifact == null) return
    addMavenCoordinatesProperties(mavenArtifact, libraryEntity)
  }

  private fun addMavenCoordinatesProperties(mavenArtifact: MavenArtifact,
                                            libraryEntity: LibraryEntity) {
    val libraryKind = ImportedLibraryType.IMPORTED_LIBRARY_KIND
    val libPropertiesElement = serialize(ImportedLibraryProperties(MavenCoordinates(mavenArtifact.groupId,
                                                                                    mavenArtifact.artifactId,
                                                                                    mavenArtifact.version,
                                                                                    mavenArtifact.packaging,
                                                                                    mavenArtifact.classifier)).state) ?: return
    libPropertiesElement.name = JpsLibraryTableSerializer.PROPERTIES_TAG
    val xmlTag = JDOMUtil.writeElement(libPropertiesElement)
    builder addEntity LibraryPropertiesEntity(libraryKind.kindId, libraryEntity.entitySource) {
      library = libraryEntity
      propertiesXmlTag = xmlTag
    }
  }

  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }

  private fun MavenProject.getManifestAttributes(): Map<String,String> {
    return this.getPluginConfiguration("org.apache.maven.plugins", "maven-jar-plugin")
      ?.getChild("archive")
      ?.getChild("manifestEntries")
      ?.children
      ?.associate { it.name to it.text } ?: emptyMap()
  }

  private fun importJavaSettings(moduleEntity: ModuleEntity,
                                 importData: MavenModuleImportData,
                                 importFolderHolder: WorkspaceFolderImporter.CachedProjectFolders) {
    val mavenProject = importData.mavenProject
    val languageLevel = MavenImportUtil.getLanguageLevel(mavenProject) { importData.moduleData.sourceLanguageLevel }

    val inheritCompilerOutput = !importingSettings.isUseMavenOutput
    var compilerOutputUrl: VirtualFileUrl? = null
    var compilerOutputUrlForTests: VirtualFileUrl? = null

    val moduleType = importData.moduleData.type

    if (!inheritCompilerOutput) {
      if (moduleType.containsMain) {
        compilerOutputUrl = virtualFileUrlManager.fromPath(importFolderHolder.outputPath)
      }
      if (moduleType.containsTest) {
        compilerOutputUrlForTests = virtualFileUrlManager.fromPath(importFolderHolder.testOutputPath)
      }
    }

    val manifestAttributes = mavenProject.getManifestAttributes()

    builder addEntity JavaModuleSettingsEntity(inheritCompilerOutput, false, moduleEntity.entitySource) {
      this.module = moduleEntity
      this.compilerOutput = compilerOutputUrl
      this.compilerOutputForTests = compilerOutputUrlForTests
      this.languageLevelId = languageLevel.name
      this.manifestAttributes = manifestAttributes
    }
  }

  companion object {
    val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")

    val EXTERNAL_SOURCE_ID get() = SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
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

      fun isFromLegacyImport(entity: ExternalSystemModuleOptionsEntity): Boolean {
        return entity.externalSystem == EXTERNAL_SOURCE_ID && entity.externalSystemModuleVersion == null
      }

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