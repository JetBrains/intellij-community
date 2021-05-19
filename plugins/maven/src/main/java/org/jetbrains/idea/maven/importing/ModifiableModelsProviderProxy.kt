// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.*
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ThrowableComputable
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

class ModifiableModelsProviderProxyImpl(private val delegate: IdeModifiableModelsProvider,
                                        private val project: Project) : ModifiableModelsProviderProxy {

  constructor(project: Project) : this(IdeModifiableModelsProviderImpl(project), project)

  var diff : WorkspaceEntityStorageBuilder? = null
  val moduleModel : ModuleModelProxy by lazy { ModuleModelProxyImpl(diff!!, project) }
  val modifiableWorkspace: ModifiableWorkspace by lazy {
    ReadAction.compute(
      ThrowableComputable<ModifiableWorkspace, RuntimeException> {
        project.service<ExternalProjectsWorkspaceImpl>()
          .createModifiableWorkspace(delegate as AbstractIdeModifiableModelsProvider)
      })
  }

  fun usingDiff(newDiff: WorkspaceEntityStorageBuilder) {
    diff = newDiff
  }

  override val modifiableModuleModel: ModifiableModuleModel
    get() = ModifiableModuleModelWrapper(moduleModel)

  override fun commit() {
    delegate.commit()
  }

  override fun dispose() {
    delegate.dispose()
  }

  override val modalityStateForQuestionDialogs: ModalityState
    get() = ModalityState.NON_MODAL


  override fun registerModulePublication(module: Module, id: ProjectCoordinate) {
    modifiableWorkspace.register(id, module);
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