// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.WorkspaceModel.Companion.getInstance
import com.intellij.workspaceModel.ide.legacyBridge.LibraryModifiableModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableFacetModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ProjectModifiableLibraryTableBridge
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder

interface ModifiableModelsProviderProxy {
  val modifiableModuleModel: ModifiableModuleModel

  // not yet implemented
  fun commit()
  fun dispose()

  val modalityStateForQuestionDialogs: ModalityState
  fun registerModulePublication(module: Module, id: ProjectCoordinate)
  fun getModifiableRootModel(module: Module?): ModifiableRootModel?
  val allLibraries: Array<Library?>
  fun removeLibrary(lib: Library?)
  fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library?
  fun getLibraryByName(name: String?): Library?
  fun getModifiableLibraryModel(library: Library?): Library.ModifiableModel?
  fun trySubstitute(module: Module?, entry: LibraryOrderEntry?, id: ProjectId?)

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
    //if (ExternalProjectsWorkspaceImpl.isDependencySubstitutionEnabled()) {
    //  updateSubstitutions()
    //}

    val projectLibrariesModel: LibraryTable.ModifiableModel = delegate.modifiableProjectLibrariesModel
    for ((fromLibrary, modifiableModel) in delegate.modifiableLibraryModels.entries) {
      val libraryName = fromLibrary.name

      // Modifiable model for the new library which was disposed via ModifiableModel.removeLibrary should also be disposed
      // Modifiable model for the old library which was removed from ProjectLibraryTable should also be disposed
      val modifiableWorkspace = delegate.modifiableWorkspace
      if (fromLibrary is LibraryEx && (fromLibrary as LibraryEx).isDisposed
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
    rootModels = existingModules.map { delegate.getModifiableRootModel(it) }.toList()
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
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState
    get() = ModalityState.NON_MODAL


  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    delegate.registerModulePublication(module, id)
  }

  override fun getModifiableRootModel(module: Module?): ModifiableRootModel? = delegate.getModifiableRootModel(module)

  override val allLibraries: Array<Library?>
    get() = delegate.allLibraries

  override fun removeLibrary(lib: Library?) {
    delegate.removeLibrary(lib)
  }

  override fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library? {
    return delegate.createLibrary(name, source)
  }

  override fun getLibraryByName(name: String?): Library? {
    return delegate.getLibraryByName(name)
  }

  override fun getModifiableLibraryModel(library: Library?): Library.ModifiableModel? {
    return delegate.getModifiableLibraryModel(library)
  }

  override fun trySubstitute(module: Module?, entry: LibraryOrderEntry?, id: ProjectId?) {
    delegate.trySubstitute(module, entry, id)
  }

  override val modifiableModelsProvider: IdeModifiableModelsProvider
    get() = delegate

}

class ModifiableModelsProviderProxyWrapper(val delegate: IdeModifiableModelsProvider) : ModifiableModelsProviderProxy {
  override val modifiableModuleModel: ModifiableModuleModel = delegate.modifiableModuleModel;

  override fun commit() {
    delegate.commit()
  }

  override fun dispose() {
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState = delegate.modalityStateForQuestionDialogs

  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    delegate.registerModulePublication(module, id)
  }

  override fun getModifiableRootModel(module: Module?): ModifiableRootModel? = delegate.getModifiableRootModel(module)

  override val allLibraries: Array<Library?> = delegate.allLibraries

  override fun removeLibrary(each: Library?) = delegate.removeLibrary(each)

  override fun createLibrary(name: String?, source: ProjectModelExternalSource?): Library? = delegate.createLibrary(name, source)

  override fun getLibraryByName(name: String?): Library? = delegate.getLibraryByName(name)

  override fun getModifiableLibraryModel(library: Library?): Library.ModifiableModel? = delegate.getModifiableLibraryModel(library)

  override fun trySubstitute(module: Module?, entry: LibraryOrderEntry?, id: ProjectId?) {
    delegate.trySubstitute(module, entry, id)
  }

  override val modifiableModelsProvider: IdeModifiableModelsProvider = delegate
}