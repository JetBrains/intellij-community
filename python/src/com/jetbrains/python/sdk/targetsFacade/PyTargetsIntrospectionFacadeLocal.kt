// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.util.annotations.paths.OsPath
import com.intellij.python.sdk.backend.localSysPathOf
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

// Local target
internal class PyTargetsIntrospectionFacadeLocal(
  private val nativeInterpreter: NativePythonInterpreter,
) : PyTargetsIntrospectionFacade(nativeInterpreter) {
  override val isLocalTarget: Boolean = true

  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) = Unit

  @RequiresBackgroundThread(generateAssertion = false)
  override fun getInterpreterPaths(): List<@OsPath String> {
    // The interpreter can be on another eel than its project.
    val eelDescriptor = nativeInterpreter.pythonBinary.getEelDescriptor()
    return localSysPathOf(super.getInterpreterPaths(), eelDescriptor)
  }
}