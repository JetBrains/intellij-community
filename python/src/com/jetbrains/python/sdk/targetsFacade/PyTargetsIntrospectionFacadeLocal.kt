// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.targetsFacade

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory

// Local target
internal class PyTargetsIntrospectionFacadeLocal(sdk: Sdk, project: Project) :
  PyTargetsIntrospectionFacade(sdk, project, PythonInterpreterTargetEnvironmentFactory.createLocalTargetRequest()) {
  override val isLocalTarget: Boolean = true

  override fun synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator: ProgressIndicator) = Unit
}