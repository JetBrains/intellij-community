// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration.interpreter

import com.intellij.openapi.project.Project
import com.intellij.python.pyproject.model.api.cleanupPyProjectBasedModules
import com.intellij.python.pyproject.model.usePyprojectToml

/**
 * Swing-free state for [PyWorkspaceStructureConfigurable]'s "Auto-configure project workspace from
 * pyproject.toml" toggle. Owns the toggle-outcome classification and the toml-off cleanup entry
 * point so a unit test can pin both without a Settings dialog fixture — the configurable only
 * renders this state and forwards user clicks back.
 */
internal class PyWorkspaceStructurePresenter {

  /** Classification of the toggle transition the configurable needs to react to on Apply. */
  enum class ToggleOutcome { ToggledOff, ToggledOn, NoChange }

  fun decideToggleOutcome(wasEnabled: Boolean, nowEnabled: Boolean): ToggleOutcome = when {
    wasEnabled && !nowEnabled -> ToggleOutcome.ToggledOff
    !wasEnabled && nowEnabled -> ToggleOutcome.ToggledOn
    else -> ToggleOutcome.NoChange
  }

  /** Flip the persisted auto-config setting iff it actually changes. */
  fun commitToggle(project: Project, wasEnabled: Boolean, nowEnabled: Boolean) {
    if (wasEnabled != nowEnabled) {
      project.usePyprojectToml = nowEnabled
    }
  }

  /**
   * Drop every pyproject-sourced module. Thin wrapper over [cleanupPyProjectBasedModules] kept
   * here so the configurable's `apply()` has one Swing-free seam the toggle test can route around.
   */
  suspend fun runToggledOffCleanup(project: Project) {
    cleanupPyProjectBasedModules(project)
  }

  /**
   * Toggle-off orchestration: let the UI tear down its own resources, then drop pyproject-sourced
   * modules. Presenter knows nothing about the UI — [disposeUi] is a Swing-free callback the
   * configurable injects. Sources commits are already synchronous by the time the configurable
   * reaches this call (see `PyModuleDetailsPane.apply`), so there is no `Job` to join.
   */
  suspend fun runToggledOffPipeline(project: Project, disposeUi: suspend () -> Unit) {
    runToggledOffPipelineCore(disposeUi) { runToggledOffCleanup(project) }
  }

  /**
   * Pure sequencing core of [runToggledOffPipeline]. Takes the workspace cleanup as a callback so
   * a unit test can pin the UI-dispose → cleanup order without a `Project` fixture.
   */
  suspend fun runToggledOffPipelineCore(disposeUi: suspend () -> Unit, runCleanup: suspend () -> Unit) {
    disposeUi()
    runCleanup()
  }
}
