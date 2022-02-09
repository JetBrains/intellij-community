// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.pom.java.LanguageLevel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.MavenRootModelAdapterInterface
import org.jetbrains.idea.maven.importing.ModifiableModelsProviderProxy
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.Path
import org.jetbrains.idea.maven.utils.Url
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.io.File

@Retention(AnnotationRetention.SOURCE)
private annotation class NotRequiredToImplement;

class MavenRootModelAdapterBridge(private val myMavenProject: MavenProject,
                                  private val module: ModuleBridge,
                                  private val project: Project,
                                  initialModuleEntity: ModuleEntity,
                                  private val legacyBridgeModifiableModelsProvider: IdeModifiableModelsProviderBridge,
                                  private val builder: WorkspaceEntityStorageBuilder) : MavenRootModelAdapterInterface {

  private var moduleEntity: ModuleEntity = initialModuleEntity
  private val legacyBridge = ModuleRootComponentBridge.getInstance(module)
  private val modifiableModel = legacyBridge.getModifiableModel(builder, RootConfigurationAccessor())
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
  private val entitySource = MavenExternalSource.INSTANCE

  override fun init(isNewlyCreatedModule: Boolean) {}
  override fun getRootModel(): ModifiableRootModel {
    return modifiableModel
  }

  override fun getSourceRootUrls(includingTests: Boolean): Array<String> {
    return legacyBridge.sourceRootUrls
  }

  override fun getModule(): Module {
    return module
  }

  @NotRequiredToImplement
  override fun clearSourceFolders() {

  }

  override fun <P : JpsElement?> addSourceFolder(path: String,
                                                 rootType: JpsModuleSourceRootType<P>) {
    createSourceRoot(rootType, path, false)

  }

  override fun addGeneratedJavaSourceFolder(path: String,
                                            rootType: JavaSourceRootType,
                                            ifNotEmpty: Boolean) {

    createSourceRoot(rootType, path, true)
  }

  override fun addGeneratedJavaSourceFolder(path: String,
                                            rootType: JavaSourceRootType) {
    createSourceRoot(rootType, path, true)
  }

  private fun <P : JpsElement?> createSourceRoot(rootType: JpsModuleSourceRootType<P>,
                                                 path: String,
                                                 generated: Boolean) {
    val typeId = getTypeId(rootType)
    val contentRootEntity = getContentRootFor(toUrl(path)) ?: error("Can't find content root for the source root $path")
    val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity,
                                                       virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(path)),
                                                       typeId,
                                                       entitySource)

    when (rootType) {
      is JavaSourceRootType -> builder.addJavaSourceRootEntity(sourceRootEntity, generated, "")
      is JavaResourceRootType -> builder.addJavaResourceRootEntity(sourceRootEntity, generated, "")
      else -> TODO()
    }
  }

  private fun <P : JpsElement?> getTypeId(rootType: JpsModuleSourceRootType<P>): String {
    return if (rootType.isForTests()) return JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID else JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
  }

  override fun hasRegisteredSourceSubfolder(f: File): Boolean {
    val url: String = toUrl(f.path).url
    return builder.entities(SourceRootEntity::class.java).filter { VfsUtilCore.isEqualOrAncestor(url, it.url.url) }.any()
  }

  private fun toUrl(path: String): Url {
    return toPath(path).toUrl()
  }

  override fun toPath(path: String): Path {
    if (!FileUtil.isAbsolute(path)) {
      return Path(File(myMavenProject.directory, path).path)
    }
    return Path(path)
  }

  override fun getSourceFolder(folder: File): SourceFolder? {
   return legacyBridge.contentEntries.flatMap { it.sourceFolders.asList() }.find { it.url == VfsUtilCore.fileToUrl(folder) }
  }

  override fun isAlreadyExcluded(f: File): Boolean {
    val url = toUrl(f.path).url
    return moduleEntity.contentRoots.filter { cre ->
      VfsUtilCore.isUnder(url, cre.excludedUrls.map { it.url })
    }.any()
  }

  override fun addExcludedFolder(path: String) {
    getContentRootFor(toUrl(path))?.let {
      builder.modifyEntity(ModifiableContentRootEntity::class.java, it) {
        this.excludedUrls = this.excludedUrls + virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(path))
      }
    }

  }

  private fun getContentRootFor(url: Url): ContentRootEntity? {
    return moduleEntity.contentRoots.firstOrNull { VfsUtilCore.isEqualOrAncestor(it.url.url, url.url) }
  }

  @NotRequiredToImplement
  override fun unregisterAll(path: String, under: Boolean, unregisterSources: Boolean) {
  }

  @NotRequiredToImplement
  override fun hasCollision(sourceRootPath: String): Boolean {
    return false
  }

  override fun useModuleOutput(production: String, test: String) {
    TODO("not implemented")
  }

  override fun addModuleDependency(moduleName: String,
                                   scope: DependencyScope,
                                   testJar: Boolean) {

    val dependency = ModuleDependencyItem.Exportable.ModuleDependency(ModuleId(moduleName), false, toEntityScope(scope), testJar)
    moduleEntity = builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      this.dependencies = this.dependencies + dependency
    }
  }

  private fun toEntityScope(scope: DependencyScope): ModuleDependencyItem.DependencyScope {
    return when (scope) {
      DependencyScope.COMPILE -> ModuleDependencyItem.DependencyScope.COMPILE
      DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
    }
  }

  override fun findModuleByName(moduleName: String): Module? {
    return ModuleManagerComponentBridge(project).modules.firstOrNull { it.name == moduleName }
  }

  private fun MavenArtifact.ideaLibraryName(): String = "${this.libraryName}";

  override fun addSystemDependency(artifact: MavenArtifact,
                                   scope: DependencyScope) {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope) { "Artifact scope should be \"system\"" }
    val roots = ArrayList<LibraryRoot>()
    roots.add(LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId.COMPILED))

    val libraryTableId = LibraryTableId.ModuleLibraryTableId(ModuleId(moduleEntity.name))

    val libraryEntity = builder.addLibraryEntity(artifact.ideaLibraryName(), libraryTableId,
                                                 roots,
                                                 emptyList(), entitySource)

    builder.addLibraryPropertiesEntity(libraryEntity, "repository", "<properties maven-id=\"${artifact.mavenId}\" />")
  }

  override fun addLibraryDependency(artifact: MavenArtifact,
                                    scope: DependencyScope,
                                    provider: ModifiableModelsProviderProxy,
                                    project: MavenProject): LibraryOrderEntry {
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId.COMPILED))
    roots.add(
      LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")),
                  WorkspaceModuleImporter.JAVADOC_TYPE))
    roots.add(
      LibraryRoot(virtualFileManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")),
                  LibraryRootTypeId.SOURCES))

    val libraryTableId = LibraryTableId.ProjectLibraryTableId; //(ModuleId(moduleEntity.name))

    val libraryEntity = builder.addLibraryEntity(artifact.ideaLibraryName(), libraryTableId,
                                                 roots,
                                                 emptyList(), entitySource)
    builder.addLibraryPropertiesEntity(libraryEntity, "repository", "<properties maven-id=\"${artifact.mavenId}\" />")

    val libDependency = ModuleDependencyItem.Exportable.LibraryDependency(LibraryId(libraryEntity.name, libraryTableId), false,
                                                                          toEntityScope(scope))

    moduleEntity = builder.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity, {
      this.dependencies += this.dependencies + libDependency
    })
    val last = legacyBridge.orderEntries.last()
    assert(last is LibraryOrderEntry && last.libraryName == artifact.ideaLibraryName())
    return last as LibraryOrderEntry
  }


  override fun findLibrary(artifact: MavenArtifact): Library? {
    return legacyBridge.getModuleLibraryTable().libraries.firstOrNull { it.name == artifact.ideaLibraryName() }
  }

  override fun setLanguageLevel(level: LanguageLevel) {
    try {
      modifiableModel.getModuleExtension(LanguageLevelModuleExtension::class.java)?.apply {
        languageLevel = level
      }
    }
    catch (e: IllegalArgumentException) { //bad value was stored
    }
  }

}