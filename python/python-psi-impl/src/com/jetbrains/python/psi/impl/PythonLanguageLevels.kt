// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The language levels of a project: a [PythonLanguageLevelLayout] for each snapshot of [EvoPyProjectModel], and for each
 * change that keeps the interpreters of a snapshot, such as a new version or new class roots, see
 * [PythonInterpreterDetails.changes].
 *
 * [PythonLanguageLevelPusher] reads [layout] for each file, and pushes each new layout to the roots, see [follow].
 */
@Service(Service.Level.PROJECT)
internal class PythonLanguageLevels(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val layoutState = MutableStateFlow(PythonLanguageLevelLayout.EMPTY)

  /** The current layout. [PythonLanguageLevelLayout.EMPTY] before the first snapshot. */
  val layout: StateFlow<PythonLanguageLevelLayout> = layoutState.asStateFlow()

  private val following = AtomicBoolean()

  /**
   * Builds the layout of each change and pushes it through [PythonLanguageLevelPusher]. The pusher calls it once for each
   * project, after it pushed the current layout.
   */
  fun follow() {
    if (!following.compareAndSet(false, true)) return
    coroutineScope.launch {
      combine(
        EvoPyProjectModel.getInstance(project).snapshotFlow(),
        PythonInterpreterDetails.getInstance(project).changes,
      ) { snapshot, _ -> PythonLanguageLevelLayout.of(project, snapshot) }
        .collect { layout ->
          layoutState.value = layout
          // On the EDT, as the roots-change cycle that calls the pusher too.
          withContext(Dispatchers.EDT) { PythonLanguageLevelPusher.layoutChanged(project, layout) }
        }
    }
  }

  /**
   * Builds the layout of the current snapshot now and keeps it. For the first push of a project, before [follow] starts.
   */
  fun refresh(): PythonLanguageLevelLayout {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull() ?: return layoutState.value
    return PythonLanguageLevelLayout.of(project, snapshot).also { layoutState.value = it }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonLanguageLevels = project.service()

    /** The current layout of [project]. */
    @JvmStatic
    fun layoutOf(project: Project): PythonLanguageLevelLayout = getInstance(project).layout.value
  }
}
