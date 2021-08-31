// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.ExternalProjectsWorkspaceImpl
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManagerEx
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
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

  // bridge back to original interface
  val modifiableModelsProvider: IdeModifiableModelsProvider?
}
class ModifiableModelsProviderProxyImpl(private val delegate: IdeModifiableModelsProviderImpl,
                                        private val project: Project) : ModifiableModelsProviderProxy {

  constructor(project: Project) : this(IdeModifiableModelsProviderImpl(project), project)

  var diff : WorkspaceEntityStorageBuilder? = null

  private val moduleModelProxy by lazy { ModuleModelProxyImpl(diff!!, project) }
  private val moduleModelWrapper by lazy { ModuleModelProxyWrapper(delegate.modifiableModuleModel) }

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
  private val librariesModel: LibraryTable.ModifiableModel by lazy {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    (libraryTable as ProjectLibraryTableBridge).getModifiableModel(diff!!)
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
    if (ExternalProjectsWorkspaceImpl.isDependencySubstitutionEnabled()) {
      delegate.forceUpdateSubstitutions()
    }

    val projectLibrariesModel: LibraryTable.ModifiableModel = delegate.modifiableProjectLibrariesModel
    for ((fromLibrary, modifiableModel) in delegate.modifiableLibraryModels.entries) {
      val libraryName = fromLibrary.name

      // Modifiable model for the new library which was disposed via ModifiableModel.removeLibrary should also be disposed
      // Modifiable model for the old library which was removed from ProjectLibraryTable should also be disposed
      val modifiableWorkspace = delegate.modifiableWorkspace
      if (fromLibrary is LibraryEx && fromLibrary.isDisposed
          || fromLibrary.table != null && libraryName != null && projectLibrariesModel.getLibraryByName(libraryName) == null
          || modifiableWorkspace != null && modifiableWorkspace.isSubstituted(fromLibrary.name)) {
        Disposer.dispose(modifiableModel)
      }
      else {
        (modifiableModel as LibraryModifiableModelBridge).prepareForCommit()
      }
    }


    (projectLibrariesModel as ProjectModifiableLibraryTableBridge).prepareForCommit()
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

  override fun dispose() {
    if (diff != null) {
      myModifiableRootModels.values
        .filter { !it.isDisposed }
        .forEach { it.dispose() }
      modifiableLibraryModels.values
        .filter { !Disposer.isDisposed(it) }
        .forEach { Disposer.dispose(it) }
      Disposer.dispose(librariesModel)
    }
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState
    get() = ModalityState.NON_MODAL


  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    delegate.modifiableWorkspace?.register(id, module)
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
          librariesModel.getLibraryByName(libraryName)
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
      librariesModel.libraries
    }

  override fun removeLibrary(lib: Library) {
    if (diff == null) {
      delegate.removeLibrary(lib)
    }
    else {
      librariesModel.removeLibrary(lib)
    }
  }

  override fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library? {
    return if (diff == null) {
      delegate.createLibrary(name, source)
    }
    else {
      librariesModel.createLibrary(name, null, source)
    }
  }

  override fun getLibraryByName(name: String): Library? {
    return if (diff == null) {
      delegate.getLibraryByName(name)
    }
    else {
      librariesModel.getLibraryByName(name)
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
      val workspaceModuleCandidate = delegate.modifiableWorkspace?.findModule(id) ?: return
      val workspaceModule = moduleModelProxy.findModuleByName(workspaceModuleCandidate) ?: return
      val modifiableRootModel = getModifiableRootModel(module)

      val moduleOrderEntry = modifiableRootModel.findModuleOrderEntry(workspaceModule)
                             ?: modifiableRootModel.addModuleOrderEntry(workspaceModule)
      moduleOrderEntry.scope = entry.scope
      moduleOrderEntry.isExported = entry.isExported
      val workspace = delegate.modifiableWorkspace!!
      workspace.addSubstitution(module.name,
                                workspaceModule.name,
                                entry.libraryName,
                                entry.scope)
      modifiableRootModel.removeOrderEntry(entry)
    }
  }

  override val modifiableModelsProvider: IdeModifiableModelsProvider
    get() = delegate

}


