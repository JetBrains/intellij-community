// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.DependencyId
import com.intellij.pycharm.community.ide.impl.configuration.interpreter.PyModuleDependenciesPresenter.OrderEntrySlot
import com.intellij.python.pyproject.model.usePyprojectToml
import com.jetbrains.python.project.PyProject

/**
 * Workspace-model port for [PyModuleDependenciesPane]. Encapsulates every `ModifiableRootModel` /
 * `ModuleOrderEntry` access so the pane stays Module-free and the port is swappable in tests.
 */
internal interface PyModuleDependenciesModel {
  /** Dependency ids currently stored on the subject module. Read from live workspace model. */
  fun liveDependencies(): Set<DependencyId>

  /** Rendered candidate ids in display order (alphabetical, case-insensitive). */
  val candidates: List<DependencyId>

  /** Baseline against which [isModified] decides whether the user changed anything. */
  val initialDependencies: Set<DependencyId>

  /** `true` while the user may change the checked set. */
  val isEditable: Boolean

  fun isModified(uiChecked: Set<DependencyId>): Boolean

  /** Apply [uiChecked] to the live workspace model, then update the baseline. Returns true on mutation. */
  fun apply(uiChecked: Set<DependencyId>): Boolean

  /** Re-read live deps into the baseline (for a UI reset). */
  fun syncBaselineFromWorkspace()
}

/** Production port backed by `ModuleRootModificationUtil` on the subject [PyProject]'s module. */
internal class PyModuleDependenciesModelImpl(
  subject: PyProject,
  candidatePyProjects: List<PyProject>,
  initialDependencies: Set<PyProject>,
) : PyModuleDependenciesModel {

  private val module: Module = subject.residesOnModule
  private val project = module.project
  private val candidatesById: Map<DependencyId, Module> =
    candidatePyProjects.associate { DependencyId(it.residesOnModule.name) to it.residesOnModule }

  private val presenter: PyModuleDependenciesPresenter<Module> = PyModuleDependenciesPresenter(
    candidates = candidatesById,
    initialDependencies = initialDependencies.mapTo(HashSet()) { DependencyId(it.residesOnModule.name) },
    isEditableProvider = { !project.usePyprojectToml },
  )

  override val candidates: List<DependencyId> get() = presenter.candidates
  override val initialDependencies: Set<DependencyId> get() = presenter.initialDependencies
  override val isEditable: Boolean get() = presenter.isEditable

  override fun liveDependencies(): Set<DependencyId> =
    ModuleRootManager.getInstance(module).dependencies.mapTo(HashSet()) { DependencyId(it.name) }

  override fun isModified(uiChecked: Set<DependencyId>): Boolean = presenter.isModified(uiChecked)

  override fun apply(uiChecked: Set<DependencyId>): Boolean {
    if (!presenter.isEditable) return false
    var mutated = false
    ModuleRootModificationUtil.updateModel(module) { model ->
      mutated = presenter.apply(
        uiChecked = uiChecked,
        entries = model.orderEntries.toList(),
        classify = { entry ->
          if (entry is ModuleOrderEntry) OrderEntrySlot.ModuleDependency(entry.moduleName)
          else OrderEntrySlot.NonModule
        },
        removeEntry = model::removeOrderEntry,
        addModule = { model.addModuleOrderEntry(it) },
      )
    }
    presenter.markApplied(uiChecked)
    return mutated
  }

  override fun syncBaselineFromWorkspace() {
    presenter.markApplied(liveDependencies())
  }
}
