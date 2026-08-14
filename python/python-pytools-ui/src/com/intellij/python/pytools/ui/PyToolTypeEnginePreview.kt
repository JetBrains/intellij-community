// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Transient, non-persisted shared state for the **staged** type-engine selection while a Settings
 * dialog is open — the single source of truth both the Type Engine page and the External Tools page
 * bind to during editing, so the engine selection and the matching tool's enable toggle stay in sync
 * live, and neither persists until Apply.
 *
 * Holds a package-name string rather than the engine enum because the External Tools module cannot
 * depend on the Type Engine module. Values: `null` — nothing is being staged (readers fall back to the
 * persisted engine); `""` — built-in engine staged (no tool); otherwise a tool package name (`"pyrefly"`).
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class PyToolTypeEnginePreview {
  val stagedEnginePackage: AtomicProperty<String?> = AtomicProperty(null)

  /**
   * Package names of engine tools the user chose to turn **off** when switching the engine away from
   * them (answering "yes" to the "turn the tool off too?" prompt). Read live by the External Tools page
   * to flip the toggle, and committed at the engine's Apply so it works without opening that page.
   */
  val pendingDisable: AtomicProperty<Set<String>> = AtomicProperty(emptySet())

  companion object {
    fun getInstance(project: Project): PyToolTypeEnginePreview = project.service()
  }
}
