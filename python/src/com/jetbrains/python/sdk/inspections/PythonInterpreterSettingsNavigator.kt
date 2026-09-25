// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.jetbrains.python.sdk.ModuleOrProject
import org.jetbrains.annotations.ApiStatus

/**
 * Opens the Python interpreter settings page for [target].
 *
 * Registered as a single application service by the higher-level module that owns the concrete
 * `Configurable` implementation — currently `intellij.pycharm.community.ide.impl` binds this to the
 * redesigned "Workspace Structure" page. Only one implementation is ever registered, so the routing
 * is an application-service lookup rather than an extension-point iteration.
 *
 * When no navigator is registered, [InterpreterSettingsQuickFix] falls back to the legacy
 * `PyActiveSdkModuleConfigurable`.
 *
 * [target] is a sealed [ModuleOrProject] instead of a `Project + Module?` pair — the sealed type
 * removes the "did the caller pass matching project + module" invariant and lets implementations
 * dispatch through an exhaustive `when`.
 */
@ApiStatus.Internal
interface PythonInterpreterSettingsNavigator {

  fun tryNavigate(target: ModuleOrProject): Boolean

  companion object {
    fun getInstance(): PythonInterpreterSettingsNavigator? =
      ApplicationManager.getApplication().serviceOrNull<PythonInterpreterSettingsNavigator>()
  }
}
