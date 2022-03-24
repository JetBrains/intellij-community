// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.facet.FacetManager
import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider.EP_NAME
import com.intellij.openapi.externalSystem.service.project.ModifiableModel
import com.intellij.openapi.externalSystem.service.project.ModifiableModelsProviderExtension
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ClassMap
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ProjectLibraryTableBridge
import org.jetbrains.idea.maven.utils.MavenLog

class IdeModifiableModelsProviderBridge(val project: Project,
                                        builder: WorkspaceEntityStorageBuilder) : IdeModifiableModelsProvider {
  private val legacyBridgeModuleManagerComponent = ModuleManagerBridgeImpl.getInstance(project)
  private val myProductionModulesForTestModules = HashMap<Module, String>()
  private val myModifiableModels = ClassMap<ModifiableModel>()
  private val myUserData = UserDataHolderBase()
  private val modifiableFacetsModels = HashMap<Module, ModifiableFacetModel>()

  var diff = builder

  init {
    EP_NAME.forEachExtensionSafe { extension: ModifiableModelsProviderExtension<ModifiableModel?> ->
      val pair = extension.create(
        project, this)
      myModifiableModels.put(pair.first, pair.second)
    }
  }

  private val modifiableModuleModel = lazy {
    legacyBridgeModuleManagerComponent.getModifiableModel(diff)
  }


  private val bridgeProjectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project) as ProjectLibraryTableBridge

  private val librariesModel = lazy {
    bridgeProjectLibraryTable.getModifiableModel(diff)
  }

  override fun findIdeLibrary(libraryData: LibraryData): Library? {
    return getAllLibraries().filter { it.name == libraryData.internalName }.firstOrNull()
  }

  override fun getAllDependentModules(module: Module): List<Module> {
    return ModuleRootComponentBridge.getInstance(module).dependencies.toList()
  }

  override fun <T : ModifiableModel?> getModifiableModel(instanceOf: Class<T>): T {
    return myModifiableModels.get(instanceOf) as? T ?: throw NotImplementedError("${instanceOf.canonicalName} not implemented")
  }

  override fun getModifiableFacetModel(module: Module): ModifiableFacetModel {
    return modifiableFacetsModels.computeIfAbsent(module) {
      (it.getComponent(FacetManager::class.java) as FacetManagerBridge).createModifiableModel(diff)
    }
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
    val modifiableModel = modifiableModuleModel.value
    legacyBridgeModuleManagerComponent.incModificationCount()
    val module = modifiableModel.newModule(filePath, moduleTypeId)
    return module
  }

  override fun newModule(moduleData: ModuleData): Module {
    val modifiableModel = modifiableModuleModel.value
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
    val bridgeLibrary = library as LibraryBridge
    return bridgeLibrary.getModifiableModel(diff)
  }

  override fun getModifiableModuleModel(): ModifiableModuleModel {
    return modifiableModuleModel.value
  }

  override fun commit() {
    if(modifiableModuleModel.isInitialized()) {
      modifiableModuleModel.value.commit()
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
    return bridgeProjectLibraryTable.createLibrary(name)
  }

  override fun createLibrary(name: String?, externalSource: ProjectModelExternalSource?): Library {
    return bridgeProjectLibraryTable.createLibrary(name)
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
    return bridgeProjectLibraryTable.libraries +
           legacyBridgeModuleManagerComponent.modules.map { ModuleRootComponentBridge(it) }
             .flatMap { it.getModuleLibraryTable().libraries.asIterable() }
  }

  override fun removeLibrary(library: Library?) {
    MavenLog.LOG.error("removeLibrary not implemented")
  }

  override fun getSourceRoots(module: Module): Array<VirtualFile> {
    return ModuleRootComponentBridge.getInstance(module).sourceRoots
  }

  override fun getSourceRoots(module: Module, includingTests: Boolean): Array<VirtualFile> {
    return ModuleRootComponentBridge.getInstance(module).getSourceRoots(includingTests)
  }

  override fun getModifiableRootModel(module: Module): ModifiableRootModel {
    return ModuleRootComponentBridge.getInstance(module).getModifiableModel()
  }

  override fun isSubstituted(libraryName: String?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getContentRoots(module: Module): Array<VirtualFile> {
    return ModuleRootComponentBridge.getInstance(module).contentRoots
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

  override fun findIdeModuleLibraryOrderEntries(moduleData: ModuleData,
                                                libraryDependencyDataList: MutableList<LibraryDependencyData>): Map<LibraryOrderEntry, LibraryDependencyData> {
    TODO("Not yet implemented")
  }

  override fun getLibraryUrls(library: Library, type: OrderRootType): Array<String> {
    return library.getUrls(type)
  }

  override fun getLibraryByName(name: String): Library? {
    return bridgeProjectLibraryTable.getLibraryByName(name)
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    return myUserData.getUserData(key)
  }

  override fun getOrderEntries(module: Module): Array<OrderEntry> {
    return ModuleRootComponentBridge.getInstance(module).orderEntries
  }

  override fun getModalityStateForQuestionDialogs(): ModalityState {
    return ModalityState.NON_MODAL
  }

  override fun dispose() {

  }

}
