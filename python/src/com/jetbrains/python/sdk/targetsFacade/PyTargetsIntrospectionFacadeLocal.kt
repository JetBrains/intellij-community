// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.util.annotations.paths.OsPath
import com.intellij.python.sdk.backend.localSysPathOf
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path

// Local target
internal class PyTargetsIntrospectionFacadeLocal(sdk: Sdk, project: Project) : PyTargetsIntrospectionFacade(sdk, project) {
  override val isLocalTarget: Boolean = true

  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) = Unit

  @RequiresBackgroundThread(generateAssertion = false)
  override fun getInterpreterPaths(): List<@OsPath String> {
    // The default project is local, and the interpreter can be on another eel.
    val eelDescriptor = Path.of(sdk.homePath ?: error("No home path for $sdk")).getEelDescriptor()
    return localSysPathOf(super.getInterpreterPaths(), eelDescriptor)
  }
}