// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Drops the resolve caches when a snapshot gives a module another interpreter.
 *
 * Resolve reads the interpreter from the snapshot, see [pythonSdkIfReady], and caches what it finds against PSI
 * changes. A snapshot lands after the roots change that set the interpreter, and before the first snapshot resolve
 * sees no interpreter at all. A daemon restart reruns the inspections on these cached results, so it is not enough.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyResolveCachesInvalidator(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val tracker = SimpleModificationTracker()

  /** Changes each time a snapshot changes the interpreter of a module. For a cache that reads the snapshot. */
  val modificationTracker: ModificationTracker get() = tracker

  internal fun subscribe() {
    coroutineScope.launch {
      EvoPyProjectModel.getInstance(project).snapshotFlow()
        .map { snapshot -> snapshot.evoPyProjects.associate { it.pyProject.residesOnModule to it.interpreter } }
        .distinctUntilChanged()
        // A project without Python projects has nothing that read the snapshot.
        .filter { it.isNotEmpty() }
        // The first generation too: resolve can run before it, and then it saw no interpreter.
        .collect { interpreters -> dropCaches(interpreters) }
    }
    coroutineScope.launch {
      // The updater writes new roots of an interpreter, which no snapshot reports. The first value is the current state.
      PythonInterpreterDetails.getInstance(project).changes.drop(1).collect {
        PythonSdkPathCache.onInterpreterDetailsChanged(project)
        tracker.incModificationCount()
        backgroundWriteAction { PsiManager.getInstance(project).dropPsiCaches() }
      }
    }
  }

  private suspend fun dropCaches(interpreters: Map<Module, PythonInterpreter?>) {
    for ((module, interpreter) in interpreters) {
      if (module.isDisposed) continue
      PythonModulePathCache.getInstance(module).clearCache()
      interpreter?.let { PythonSdkPathCache.getInstance(project, it).clearCache() }
    }
    tracker.incModificationCount()
    backgroundWriteAction { PsiManager.getInstance(project).dropPsiCaches() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyResolveCachesInvalidator = project.service()
  }
}

/** Starts [PyResolveCachesInvalidator]. Ends at once, so a test that waits for the startup activities does not hang. */
internal class PyResolveCachesInvalidatorActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    PyResolveCachesInvalidator.getInstance(project).subscribe()
  }
}
