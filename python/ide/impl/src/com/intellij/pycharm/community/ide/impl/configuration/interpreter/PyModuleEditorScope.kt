// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-[PyProject] coroutine scope. Scope dies with the backing module via a Disposer hook — any
 * coroutine launched on it stops silently when the module is disposed, which replaces every
 * `module.isDisposed` fence with coroutine cancellation.
 *
 * [PyProjectImpl][com.intellij.python.pyproject.model.internal.pyProject.PyProjectImpl] is a
 * `data class` keyed by `baseDir + residesOnModule`, so two [PyProject] instances for the same
 * module compare equal and share one scope — exactly what the `computeIfAbsent` map needs.
 *
 * See `community/python/exec-conventions.md` → "Scope-based lifecycle".
 */
@Service(Service.Level.PROJECT)
internal class PyModuleEditorScopes(private val projectScope: CoroutineScope) {

  private val byPyProject = ConcurrentHashMap<PyProject, CoroutineScope>()

  fun scopeFor(pyProject: PyProject): CoroutineScope =
    byPyProject.computeIfAbsent(pyProject) {
      val module = pyProject.residesOnModule
      val child = projectScope.childScope("PyModuleEditorScope(${module.name})")
      // Platform cancels the project scope on project dispose. The module can die earlier, so hook
      // the module Disposer too: `cancel()` propagates to launched coroutines, `remove` keeps the
      // map from leaking entries across settings reopens.
      Disposer.register(module) {
        byPyProject.remove(pyProject)
        child.cancel()
      }
      child
    }

  companion object {
    fun of(pyProject: PyProject): CoroutineScope =
      pyProject.residesOnModule.project.service<PyModuleEditorScopes>().scopeFor(pyProject)
  }
}
