// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration.suppressors

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.sdk.configuration.PythonSdkCreationWaiter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun suppressTipAndInspectionsFor(module: Module, debugName: String): Disposable {
  val project = module.project

  val lifetime = Disposer.newDisposable(
    PythonPluginDisposable.getInstance(project),
    "Configuring sdk using $debugName"
  )

  TipOfTheDaySuppressor.suppress()?.let { Disposer.register(lifetime, it) }
  Disposer.register(lifetime, PyPackageRequirementsInspectionSuppressor(module))

  PythonSdkCreationWaiter.register(module, lifetime)
  return lifetime
}
