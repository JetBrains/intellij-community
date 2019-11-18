// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.worktree

import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ModifiableModel
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleManagerComponent
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleRootComponent
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeProjectLibraryTableImpl
import org.jetbrains.idea.maven.utils.MavenLog

class LegacyBrigdeIdeModifiableModelsProvider(val project: Project,
                                              builder: TypedEntityStorageBuilder) : IdeModifiableModelsProvider {
  val diff = builder
  private val legacyBridgeModuleManagerComponent = LegacyBridgeModuleManagerComponent.getInstance(project)
  private val myProductionModulesForTestModules = HashMap<Module, String>()
  private val myUserData = UserDataHolderBase()

  private val moduleModel = lazy {
    legacyBridgeModuleManagerComponent.getModifiableModel(diff)
  }

  private val projectLibraryTableImpl = ProjectLibraryTable.getInstance(project) as LegacyBridgeProjectLibraryTableImpl

  private val librariesModel = lazy {
    projectLibraryTableImpl.getModifiableModel(diff)
  }


  override fun findIdeLibrary(libraryData: LibraryData): Library? {
    return getAllLibraries().filter { it.name == libraryData.internalName }.firstOrNull()
  }

  override fun getAllDependentModules(module: Module): List<Module> {
    return LegacyBridgeModuleRootComponent.getInstance(module).dependencies.toList()
  }

  override fun <T : ModifiableModel?> getModifiableModel(instanceOf: Class<T>): T {
    TODO()
  }

  override fun getModifiableFacetModel(module: Module?): ModifiableFacetModel {
    TODO()
  }

  override fun findIdeModule(module: ModuleData): Module? {
    return findIdeModule(module.moduleName)
  }

  override fun findIdeModule(ideModuleName: String): Module? {
    return legacyBridgeModuleManagerComponent.findModuleByName(ideModuleName)
  }

  override fun newModule(filePath: String, moduleTypeId: String?): Module {
    if (moduleTypeId == null) {
      throw IllegalArgumentException("moduleTypeId")
    }
    legacyBridgeModuleManagerComponent.incModificationCount()
    val modifiableModel = legacyBridgeModuleManagerComponent.getModifiableModel(diff)
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    return module;
  }

  override fun newModule(moduleData: ModuleData): Module {
    val modifiableModel = moduleModel.value
    legacyBridgeModuleManagerComponent.incModificationCount()
    val newModule = modifiableModel.newModule(moduleData.moduleFileDirectoryPath, moduleData.moduleTypeId)
    return newModule
  }

  override fun getModifiableProjectLibrariesModel(): LibraryTable.ModifiableModel {
    return librariesModel.value
  }

  override fun getProductionModuleName(module: Module?): String? {
    return myProductionModulesForTestModules[module]
  }

  override fun getModifiableLibraryModel(library: Library?): Library.ModifiableModel {
    val bridgeLibraryImpl = library as LegacyBridgeLibraryImpl
    return bridgeLibraryImpl.getModifiableModel(diff)
  }

  override fun getModifiableModuleModel(): ModifiableModuleModel {
    return legacyBridgeModuleManagerComponent.getModifiableModel(diff)
  }

  override fun commit() {
    if(moduleModel.isInitialized()) {
      moduleModel.value.commit()
    }
  }

  override fun setTestModuleProperties(testModule: Module, productionModuleName: String) {
    myProductionModulesForTestModules[testModule] = productionModuleName
  }

  override fun trySubstitute(ownerModule: Module?,
                             libraryOrderEntry: LibraryOrderEntry?,
                             publicationId: ProjectCoordinate?): ModuleOrderEntry? {
//    MavenLog.LOG.error("trySubstitute not implemented")
    return null
  }

  override fun findIdeModuleDependency(dependency: ModuleDependencyData, module: Module): ModuleOrderEntry? {
    MavenLog.LOG.error("findIdeModuleDependency not implemented")
    return null
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    myUserData.putUserData(key, value)
  }

  override fun getUnloadedModuleDescription(moduleData: ModuleData): UnloadedModuleDescription? {
    MavenLog.LOG.error("getUnloadedModuleDescription not implemented")
    return null
  }

  override fun createLibrary(name: String?): Library {
    return projectLibraryTableImpl.createLibrary(name)
  }

  override fun createLibrary(name: String?, externalSource: ProjectModelExternalSource?): Library {
    return projectLibraryTableImpl.createLibrary(name)
  }

  override fun getModules(): Array<Module> {
    return legacyBridgeModuleManagerComponent.modules
  }

  override fun getModules(projectData: ProjectData): Array<Module> {
    return legacyBridgeModuleManagerComponent.modules
  }

  override fun registerModulePublication(module: Module?, modulePublication: ProjectCoordinate?) {
    //MavenLog.LOG.error("registerModulePublication not implemented")
  }

  override fun getAllLibraries(): Array<Library> {
    return projectLibraryTableImpl.libraries +
           legacyBridgeModuleManagerComponent.modules.map { LegacyBridgeModuleRootComponent(it) }
             .flatMap { it.legacyBridgeModuleLibraryTable().libraries.asIterable() }
  }

  override fun removeLibrary(library: Library?) {
    MavenLog.LOG.error("removeLibrary not implemented")
  }

  override fun getSourceRoots(module: Module): Array<VirtualFile> {
    return LegacyBridgeModuleRootComponent.getInstance(module).sourceRoots
  }

  override fun getSourceRoots(module: Module, includingTests: Boolean): Array<VirtualFile> {
    return LegacyBridgeModuleRootComponent.getInstance(module).getSourceRoots(includingTests)
  }

  override fun getModifiableRootModel(module: Module): ModifiableRootModel {
    return LegacyBridgeModuleRootComponent.getInstance(module).getModifiableModel()
  }

  override fun isSubstituted(libraryName: String?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getContentRoots(module: Module): Array<VirtualFile> {
    return LegacyBridgeModuleRootComponent.getInstance(module).contentRoots
  }

  override fun <T : ModifiableModel?> findModifiableModel(instanceOf: Class<T>): T? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun findModuleByPublication(publicationId: ProjectCoordinate?): String? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun findIdeModuleOrderEntry(data: DependencyData<*>): OrderEntry? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getLibraryUrls(library: Library, type: OrderRootType): Array<String> {
    return library.getUrls(type)
  }

  override fun getLibraryByName(name: String): Library? {
    return projectLibraryTableImpl.getLibraryByName(name)
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    return myUserData.getUserData(key)
  }

  override fun getOrderEntries(module: Module): Array<OrderEntry> {
    return LegacyBridgeModuleRootComponent.getInstance(module).orderEntries
  }

  override fun getModalityStateForQuestionDialogs(): ModalityState {
    return ModalityState.NON_MODAL
  }

  override fun dispose() {

  }

}
