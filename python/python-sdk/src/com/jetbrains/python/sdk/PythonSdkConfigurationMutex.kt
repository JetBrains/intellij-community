// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Per-project mutex that serializes Python SDK configuration operations — both
 * auto-configuration on startup and manual SDK creation from the UI.
 *
 * This is per-project (not global) so that SDK configuration in one project
 * does not trigger spurious "Checking existing environments" notifications in others.
 *
 * Within a project the mutex is still global (not per-module) because setting an SDK on one module
 * can affect others via inherited project SDK in multi-module workspaces.
 *
 * Observe [isLocked][ObservableMutex.isLocked] to track whether an SDK configuration is running.
 */
val Project.pythonSdkConfigurationMutex: ObservableMutex
  @ApiStatus.Internal
  get() = service<PythonSdkConfigurationMutexService>().mutex


@Service(Service.Level.PROJECT)
internal class PythonSdkConfigurationMutexService {
  val mutex: ObservableMutex = ObservableMutex()
}
