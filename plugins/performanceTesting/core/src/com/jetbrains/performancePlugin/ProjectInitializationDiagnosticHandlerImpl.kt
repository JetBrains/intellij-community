// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticHandler
import com.intellij.internal.performanceTests.ProjectInitializationDiagnosticService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.function.Supplier

internal class ProjectInitializationDiagnosticHandlerImpl : ProjectInitializationDiagnosticHandler {
  override fun registerBeginningOfInitializationActivity(
    project: Project,
    debugMessageProducer: Supplier<String>,
  ): ProjectInitializationDiagnosticService.ActivityTracker {
    return project.service<ProjectInitializationDiagnosticServiceImpl>().registerBeginningOfInitializationActivity(debugMessageProducer)
  }

  override fun isProjectInitializationAndIndexingFinished(project: Project): Boolean {
    return project.service<ProjectInitializationDiagnosticServiceImpl>().isProjectInitializationAndIndexingFinished()
  }
}
