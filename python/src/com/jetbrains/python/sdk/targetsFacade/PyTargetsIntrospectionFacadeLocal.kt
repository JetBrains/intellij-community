// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlin.io.path.pathString

// Local target
internal class PyTargetsIntrospectionFacadeLocal(sdk: Sdk, project: Project) : PyTargetsIntrospectionFacade(sdk, project) {
  override val isLocalTarget: Boolean = true

  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) = Unit
  @RequiresBackgroundThread(generateAssertion = false)
  override fun getInterpreterPaths(): List<String> =
    super.getInterpreterPaths().map { EelPath.parse(it, project.getEelDescriptor()).asNioPath().pathString }
}