// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.languageLevel
import com.jetbrains.python.psi.LanguageLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The language levels of a project: a [PythonLanguageLevelLayout] for each snapshot of [EvoPyProjectModel], and for each
 * change that keeps the interpreters of a snapshot: new class roots, see [PythonInterpreterRoots.changes], or a new
 * version, see [PythonInterpreterDetails.changes].
 *
 * [PythonLanguageLevelPusher] reads [layout] for each file. A new layout becomes [layout] in the same step that pushes it
 * to the roots, so a file never reads a level that the roots do not have yet, see [start].
 */
@Service(Service.Level.PROJECT)
internal class PythonLanguageLevels(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val layoutState = MutableStateFlow(PythonLanguageLevelLayout.EMPTY)

  /** The layout that the pusher pushed last. [PythonLanguageLevelLayout.EMPTY] before the first push. */
  val layout: StateFlow<PythonLanguageLevelLayout> = layoutState.asStateFlow()

  private val started = AtomicBoolean()

  /**
   * Pushes the layout of the current snapshot, and then each new layout. The pusher calls it once, before the first scan
   * of the project, so the scan reads the levels of the snapshot. Later layouts become [layout] on the EDT with their push.
   *
   * The service does not start itself. A reader of a level can create it first, in a read action, and a push there queues
   * a dumb task, which takes a write action. Until this call, a reader gets [PythonLanguageLevelLayout.EMPTY], and the
   * first layout has one writer: this call computes it before the collector starts.
   */
  fun start() {
    if (!started.compareAndSet(false, true)) return
    // No collector runs yet, so this thread is the only writer.
    val first = EvoPyProjectModel.getInstance(project).snapshotOrNull()?.let { PythonLanguageLevelLayout.of(project, it) }
    if (first != null) {
      PythonLanguageLevelPusher.levelsChanged()
      PythonLanguageLevelPusher.push(project, first, layoutState.getAndUpdate { first })
    }
    coroutineScope.launch {
      combine(
        EvoPyProjectModel.getInstance(project).snapshotFlow(),
        PythonInterpreterRoots.getInstance(project).changes,
        // A new version changes the level and can keep the roots, and a change that keeps the roots reports nothing on
        // `changes`. The details report it as an event, so the flow starts with a value of its own.
        PythonInterpreterDetails.getInstance(project).changes.map { }.onStart { emit(Unit) },
      ) { snapshot, _, _ -> PythonLanguageLevelLayout.of(project, snapshot) }
        .distinctUntilChanged()
        // While the EDT is busy, for example under a modal dialog, only the newest layout is worth a push.
        .conflate()
        .collect { layout ->
          withContext(Dispatchers.EDT) {
            val previous = layoutState.getAndUpdate { layout }
            if (layout != previous) {
              PythonLanguageLevelPusher.levelsChanged()
              PythonLanguageLevelPusher.push(project, layout, previous)
            }
          }
        }
    }
  }

  /**
   * For tests: suspends until the pusher has pushed the layout of the current snapshot. A layout that comes later can
   * start a dumb mode in a test.
   */
  @TestOnly
  suspend fun awaitPushedForTests() {
    val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
    val expected = PythonLanguageLevelLayout.of(project, snapshot)
    layout.first { it == expected }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonLanguageLevels = project.service()

    /** The current layout of [project]. */
    @JvmStatic
    fun layoutOf(project: Project): PythonLanguageLevelLayout = getInstance(project).layout.value

    /**
     * The level of [interpreter] in the current layout of [project]. When the layout does not have [interpreter] yet, the
     * level comes from its version, see [languageLevel].
     */
    @JvmStatic
    fun levelOf(project: Project, interpreter: PythonInterpreter): LanguageLevel =
      layoutOf(project).interpreterLevels[interpreter] ?: interpreter.languageLevel(project)
  }
}

/** For tests: the layout that the pusher pushed last and the layout of the current snapshot, for the report of a wait that does not end. */
@ApiStatus.Internal
@TestOnly
fun describePythonLanguageLevelsForTests(project: Project): String {
  val levels = PythonLanguageLevels.getInstance(project)
  val expected = EvoPyProjectModel.getInstance(project).snapshotOrNull()?.let { PythonLanguageLevelLayout.of(project, it) }
  return "language levels: pushed ${levels.layout.value}, expected ${expected}"
}

/** For tests: [PythonLanguageLevels.awaitPushedForTests] of [project]. */
@ApiStatus.Internal
@TestOnly
suspend fun awaitPythonLanguageLevelsForTests(project: Project) {
  PythonLanguageLevels.getInstance(project).awaitPushedForTests()
}
