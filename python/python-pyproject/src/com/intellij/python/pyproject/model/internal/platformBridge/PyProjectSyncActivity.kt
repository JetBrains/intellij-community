package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.python.pyproject.model.internal.startAutoImportIfNeeded

internal class PyProjectSyncActivity : ProjectActivity {
  private companion object {
    val log = fileLogger()
  }

  override suspend fun execute(project: Project) {
    if (project.isDefault) return // Service doesn't support default project

    // For newly created projects, lots of files are generated in background by so-called `PyV3` framework.
    // This is done in sync. manner, so we can't rebuild project until they finish, and we let them call startAutoImportIfNeeded.
    if (!PlatformProjectOpenProcessor.isNewlyCreatedProject(project)) {
      log.info("Import started by sync activity")
      startAutoImportIfNeeded(project, "Sync activity")
    }
  }
}
