// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration.suppressors

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.sdk.configuration.PythonSdkCreationWaiter
import org.jetbrains.annotations.ApiStatus

/**
 * Holds back the tip of the day while an interpreter is configured, until the returned handle is disposed.
 *
 * The requirements inspection is not held back from here any more. It used to be, through a flag on [module] alone,
 * which left every other module the interpreter is applied to analysed against a half-built environment — a workspace
 * is configured as a whole (PY-90174). It reads [com.jetbrains.python.sdk.isSdkConfigurationInProgress] instead: every
 * path that configures an interpreter already holds the configuration mutex, which reports it for the whole project.
 */
@ApiStatus.Internal
fun suppressTipAndInspectionsFor(module: Module, debugName: String): Disposable {
  val project = module.project

  val lifetime = Disposer.newDisposable(
    PythonPluginDisposable.getInstance(project),
    "Configuring sdk using $debugName"
  )

  TipOfTheDaySuppressor.suppress()?.let { Disposer.register(lifetime, it) }

  PythonSdkCreationWaiter.register(module, lifetime)
  return lifetime
}
