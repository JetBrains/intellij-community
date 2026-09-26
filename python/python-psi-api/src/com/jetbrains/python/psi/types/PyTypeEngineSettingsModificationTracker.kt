// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.ApiStatus

/**
 * Modification tracker that should be incremented when the engine that a new type context picks can change.
 * That happens when the user switches between type engines, and when the server of the selected external
 * engine starts or stops. Caches that depend on the engine (like TypeEvalContextCache) are then invalidated.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyTypeEngineSettingsModificationTracker : SimpleModificationTracker() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyTypeEngineSettingsModificationTracker = project.service()
  }
}
