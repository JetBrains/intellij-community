// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.evolution

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Creates [EvoPyProjectModel] when the project opens, so the first snapshot is usually ready before the first highlighting pass. */
internal class EvoPyProjectModelStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (project.isDefault) return
    EvoPyProjectModel.getInstance(project)
  }
}
