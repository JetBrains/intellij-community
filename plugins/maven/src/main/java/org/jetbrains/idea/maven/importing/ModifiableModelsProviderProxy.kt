// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.workspaceModel.ide.WorkspaceModel.Companion.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleRootComponentBridge
import com.intellij.workspaceModel.ide.legacyBridge.*
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import java.util.*

interface ModifiableModelsProviderProxy {
  val modifiableModuleModel: ModifiableModuleModel

  // not yet implemented
  fun commit()
  fun dispose()

  val modalityStateForQuestionDialogs: ModalityState
  fun registerModulePublication(module: Module, id: ProjectCoordinate)
  fun getModifiableRootModel(module: Module): ModifiableRootModel
  val allLibraries: Array<Library?>
  fun removeLibrary(lib: Library)
  fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library?
  fun getLibraryByName(name: String): Library?
  fun getModifiableLibraryModel(library: Library): Library.ModifiableModel?
  fun trySubstitute(module: Module, entry: LibraryOrderEntry, id: ProjectId)
}


class ModifiableModelsProviderProxyImpl(private val delegate: IdeModifiableModelsProviderImpl,
                                        private val project: Project) : ModifiableModelsProviderProxy {

  constructor(project: Project) : this(IdeModifiableModelsProviderImpl(project), project)

  var diff : WorkspaceEntityStorageBuilder? = null

  private val moduleModelProxy by lazy { ModuleModelProxyImpl(diff!!, project) }
  private val moduleModelWrapper by lazy { ModuleModelProxyWrapper(delegate.modifiableModuleModel) }
  private val substitutionWorkspace by lazy {
    ReadAction.compute(
      ThrowableComputable<ModifiableWorkspace, RuntimeException> {
        project.getService(ExternalProjectsWorkspaceImpl::class.java)
          .createModifiableWorkspace { moduleModel.modules.asList() }
      })
  }

  val moduleModel : ModuleModelProxy
    get() {
      return if (diff != null) {
        moduleModelProxy
      }
      else {
        moduleModelWrapper
      }
    }

  private val myModifiableRootModels: MutableMap<Module, ModifiableRootModel> = HashMap()
  private val modifiableLibraryModels: MutableMap<Library, Library.ModifiableModel> = IdentityHashMap()
  private val modifiableLibraryTable: ProjectModifiableLibraryTableBridge by lazy {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    (libraryTable as ProjectLibraryTableBridge).getModifiableModel(diff!!) as ProjectModifiableLibraryTableBridge
  }


  fun usingDiff(newDiff: WorkspaceEntityStorageBuilder) {
    diff = newDiff
  }

  override val modifiableModuleModel: ModifiableModuleModel
    get() = ModifiableModuleModelWrapper(moduleModel)

  override fun commit() {
    val finalDiff = diff
    if (finalDiff == null) {
      delegate.commit()
    } else {
      ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
        workspaceCommit(finalDiff)
      }
    }
  }

  private fun workspaceCommit(diff: WorkspaceEntityStorageBuilder) {
    updateSubstitutions()

    for ((fromLibrary, modifiableModel) in modifiableLibraryModels.entries) {
      val libraryName = fromLibrary.name

      // Modifiable model for the new library which was disposed via ModifiableModel.removeLibrary should also be disposed
      // Modifiable model for the old library which was removed from ProjectLibraryTable should also be disposed
      if (fromLibrary is LibraryEx && fromLibrary.isDisposed
          || fromLibrary.table != null && libraryName != null && modifiableLibraryTable.getLibraryByName(libraryName) == null
          || substitutionWorkspace != null && substitutionWorkspace.isSubstituted(fromLibrary.name)) {
        Disposer.dispose(modifiableModel)
      }
      else {
        (modifiableModel as LibraryModifiableModelBridge).prepareForCommit()
      }
    }
    modifiableLibraryTable.prepareForCommit()

    val rootModels: List<ModifiableRootModel>
    val existingModules: Array<Module> = moduleModel.modules
    for (module in existingModules) {
      module.putUserData(IdeModifiableModelsProviderImpl.MODIFIABLE_MODELS_PROVIDER_KEY, null)
    }
    rootModels = existingModules.map { getModifiableRootModel(it) }.toList()
    //moduleModel.prepareForCommit();

    for (model in rootModels) {
      assert(!model.isDisposed) { "Already disposed: $model" }
    }
    for (model in rootModels) {
      (model as ModifiableRootModelBridge).prepareForCommit()
    }

    for ((key, value) in delegate.modifiableFacetModels.entries) {
      if (!key.isDisposed) {
        (value as ModifiableFacetModelBridge).prepareForCommit()
      }
    }
    delegate.modifiableModels.values().forEach { it.commit() }

    getInstance(project).updateProjectModel<Any?> { builder: WorkspaceEntityStorageBuilder ->
      builder.addDiff(diff)
      null
    }

    for (model in rootModels) {
      (model as ModifiableRootModelBridge).postCommit()
    }
  }

  private fun updateSubstitutions() {
    if (!ExternalProjectsWorkspaceImpl.isDependencySubstitutionEnabled()) {
      return
    }

    val oldModules = ModuleManager.getInstance(project).modules.map { it.name }
    val newModules = moduleModel.modules.mapTo(HashSet<String>()) { it.name }

    val removedModules: MutableCollection<String> = HashSet(oldModules)
    removedModules.removeAll(newModules)

    val toSubstitute: MutableMap<String, String> = HashMap()
    val projectDataManager = ProjectDataManager.getInstance()

    ExternalSystemManager.EP_NAME.iterable.asSequence()
      .flatMap { projectDataManager.getExternalProjectsData(project, it.systemId) }
      .mapNotNull { it.externalProjectStructure }
      .flatMap { ExternalSystemApiUtil.findAll(it, ProjectKeys.LIBRARY) }
      .forEach {
        val libraryData = it.data
        val substitutionModuleCandidate = substitutionWorkspace.findModule(libraryData)
        if (substitutionModuleCandidate != null) {
          toSubstitute[libraryData.internalName] = substitutionModuleCandidate
        }
      }

    for (module in moduleModel.modules) {
      val modifiableRootModel = getModifiableRootModel(module)
      var changed = false
      val entries = modifiableRootModel.orderEntries
      var i = 0
      val length = entries.size
      while (i < length) {
        val orderEntry = entries[i]
        if (orderEntry is ModuleOrderEntry) {
          val workspaceModule = orderEntry.moduleName
          if (removedModules.contains(workspaceModule)) { // check if removed module was a dependency substitution and restore library dependency
            val scope = orderEntry.scope
            if (substitutionWorkspace.isSubstitution(module.name, workspaceModule, scope)) {
              val libraryName = substitutionWorkspace.getSubstitutedLibrary(workspaceModule)
              if (libraryName != null) {
                val library = getLibraryByName(libraryName)
                if (library != null) {
                  modifiableRootModel.removeOrderEntry(orderEntry)
                  entries[i] = modifiableRootModel.addLibraryEntry(library)
                  changed = true
                  substitutionWorkspace.removeSubstitution(module.name, workspaceModule, libraryName, scope)
                }
              }
            }
          }
        }
        if (orderEntry !is LibraryOrderEntry) {
          i++
          continue
        }
        if (!orderEntry.isModuleLevel && orderEntry.libraryName != null) {
          val workspaceModule = toSubstitute[orderEntry.libraryName] // check if we can substitute a dependency and do it
          if (workspaceModule != null) {
            val ideModule: Module? = findIdeModule(workspaceModule)
            if (ideModule != null) {
              val moduleOrderEntry = modifiableRootModel.addModuleOrderEntry(ideModule)
              moduleOrderEntry.scope = orderEntry.scope
              modifiableRootModel.removeOrderEntry(orderEntry)
              entries[i] = moduleOrderEntry
              changed = true
              substitutionWorkspace.addSubstitution(module.name, workspaceModule,
                orderEntry.libraryName,
                orderEntry.scope)
            }
          }
        }
        i++
      }
      if (changed) {
        modifiableRootModel.rearrangeOrderEntries(entries)
      }
    }

    substitutionWorkspace.commit()
  }

  fun findIdeModule(ideModuleName: String): Module? {
    val module: Module? = modifiableModuleModel.findModuleByName(ideModuleName)
    return module ?: modifiableModuleModel.getModuleToBeRenamed(ideModuleName)
  }

  override fun dispose() {
    if (diff != null) {
      myModifiableRootModels.values
        .filter { !it.isDisposed }
        .forEach { it.dispose() }
      modifiableLibraryModels.values
        .filter { !Disposer.isDisposed(it) }
        .forEach { Disposer.dispose(it) }
      Disposer.dispose(modifiableLibraryTable)
    }
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState
    get() = ModalityState.NON_MODAL


  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    substitutionWorkspace.register(id, module)
  }

  override fun getModifiableRootModel(module: Module): ModifiableRootModel {
    return if (diff == null) {
      delegate.getModifiableRootModel(module)
    } else {
      myModifiableRootModels.getOrPut(module) { doGetModifiableRootModel(module) }
    }
  }

  private fun doGetModifiableRootModel(module: Module): ModifiableRootModel {
    val rootConfigurationAccessor: RootConfigurationAccessor = object : RootConfigurationAccessor() {
      override fun getLibrary(library: Library?, libraryName: String, libraryLevel: String): Library? {
        return if (LibraryTablesRegistrar.PROJECT_LEVEL == libraryLevel) {
          modifiableLibraryTable.getLibraryByName(libraryName)
        }
        else library
      }
    }

    return ReadAction.compute<ModifiableRootModel, RuntimeException> {
      val rootManager = ModuleRootManagerEx.getInstanceEx(module)
      val initialStorage = getInstance(project).entityStorage.current
      (rootManager as ModuleRootComponentBridge).getModifiableModel(diff!!, rootConfigurationAccessor)
    }
  }


  override val allLibraries: Array<Library?>
    get() = if (diff == null) {
      delegate.allLibraries
    }
    else {
      modifiableLibraryTable.libraries
    }

  override fun removeLibrary(lib: Library) {
    if (diff == null) {
      delegate.removeLibrary(lib)
    }
    else {
      modifiableLibraryTable.removeLibrary(lib)
    }
  }

  override fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library? {
    return if (diff == null) {
      delegate.createLibrary(name, source)
    }
    else {
      modifiableLibraryTable.createLibrary(name, null, source)
    }
  }

  override fun getLibraryByName(name: String): Library? {
    return if (diff == null) {
      delegate.getLibraryByName(name)
    }
    else {
      modifiableLibraryTable.getLibraryByName(name)
    }
  }

  override fun getModifiableLibraryModel(library: Library): Library.ModifiableModel? {
    return if (diff == null) {
      delegate.getModifiableLibraryModel(library)
    } else {
      modifiableLibraryModels.getOrPut(library) { (library as LibraryBridge).getModifiableModel(diff!!) }
    }
  }

  override fun trySubstitute(module: Module, entry: LibraryOrderEntry, id: ProjectId) {
    if (diff == null) {
      delegate.trySubstitute(module, entry, id)
    } else {
      val workspaceModuleCandidate = substitutionWorkspace.findModule(id) ?: return
      val workspaceModule = moduleModelProxy.findModuleByName(workspaceModuleCandidate) ?: return
      val modifiableRootModel = getModifiableRootModel(module)

      val moduleOrderEntry = modifiableRootModel.findModuleOrderEntry(workspaceModule)
                             ?: modifiableRootModel.addModuleOrderEntry(workspaceModule)
      moduleOrderEntry.scope = entry.scope
      moduleOrderEntry.isExported = entry.isExported
      substitutionWorkspace.addSubstitution(module.name,
                                workspaceModule.name,
                                entry.libraryName,
                                entry.scope)
      modifiableRootModel.removeOrderEntry(entry)
    }
  }
}


class ModifiableModelsProviderProxyWrapper(private val delegate: IdeModifiableModelsProvider) : ModifiableModelsProviderProxy {
  override val modifiableModuleModel: ModifiableModuleModel
    get() = delegate.modifiableModuleModel

  override fun commit() {
    delegate.commit()
  }

  override fun dispose() {
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState
    get() = delegate.modalityStateForQuestionDialogs

  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    delegate.registerModulePublication(module, id)
  }

  override fun getModifiableRootModel(module: Module): ModifiableRootModel {
    return delegate.getModifiableRootModel(module)
  }

  override val allLibraries: Array<Library?>
    get() = delegate.allLibraries

  override fun removeLibrary(lib: Library) {
    delegate.removeLibrary(lib)
  }

  override fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library? {
    return delegate.createLibrary(name, source)
  }

  override fun getLibraryByName(name: String): Library? {
    return delegate.getLibraryByName(name)
  }

  override fun getModifiableLibraryModel(library: Library): Library.ModifiableModel? {
    return delegate.getModifiableLibraryModel(library)
  }

  override fun trySubstitute(module: Module, entry: LibraryOrderEntry, id: ProjectId) {
    delegate.trySubstitute(module, entry, id)
  }
}

