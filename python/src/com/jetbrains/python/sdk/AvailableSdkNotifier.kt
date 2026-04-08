// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.python.common.sdk.sendPythonAvailableEvent

internal class AvailableSdkNotifier : ModuleRootListener {
  override fun rootsChanged(event: ModuleRootEvent) {
    sendPythonAvailableEventFor(event.project)
  }
}

internal class PostStartupAvailableSdkNotifier : ProjectActivity {
  override suspend fun execute(project: Project) {
    sendPythonAvailableEventFor(project)
  }
}

private fun sendPythonAvailableEventFor(project: Project) {
  sendPythonAvailableEvent(
    project,
    project.modules.any { module ->
      !module.isDisposed && module.pythonSdk != null
    }
  )
}
