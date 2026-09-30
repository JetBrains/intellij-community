// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path
import kotlin.io.path.pathString

// Local target
internal class PyTargetsIntrospectionFacadeLocal(sdk: Sdk, project: Project) : PyTargetsIntrospectionFacade(sdk, project) {
  override val isLocalTarget: Boolean = true

  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) = Unit
  @RequiresBackgroundThread(generateAssertion = false)
  override fun getInterpreterPaths(): List<String> {
    // sys.path is in the eel of the interpreter. The eel of the project can be another one (i.e. the default project is local).
    val eelDescriptor = Path.of(sdk.homePath ?: error("No home path for $sdk")).getEelDescriptor()
    return super.getInterpreterPaths().map { EelPath.parse(it, eelDescriptor).asNioPath().pathString }
  }
}