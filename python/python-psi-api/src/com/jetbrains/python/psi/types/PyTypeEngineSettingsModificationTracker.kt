// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.annotations.ApiStatus

/**
 * Modification tracker that should be incremented when type engine settings change.
 * This allows caches that depend on type engine settings (like TypeEvalContextCache)
 * to be invalidated when the user switches between type engines.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyTypeEngineSettingsModificationTracker : SimpleModificationTracker() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): PyTypeEngineSettingsModificationTracker = project.service()
  }
}
