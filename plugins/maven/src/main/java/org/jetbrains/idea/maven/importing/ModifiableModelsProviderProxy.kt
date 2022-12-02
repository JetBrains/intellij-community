// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library

interface ModifiableModelsProviderProxy {
  val moduleModelProxy: ModuleModelProxy

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

class ModifiableModelsProviderProxyWrapper(private val delegate: IdeModifiableModelsProvider) : ModifiableModelsProviderProxy {

  constructor(project: Project) : this(ProjectDataManager.getInstance().createModifiableModelsProvider(project))

  override val moduleModelProxy: ModuleModelProxy
    get() = ModuleModelProxyWrapper(delegate.modifiableModuleModel)

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

