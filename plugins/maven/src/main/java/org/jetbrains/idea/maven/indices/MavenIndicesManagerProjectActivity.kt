// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MavenIndicesManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    blockingContext {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        MavenIndicesManager.getInstance(project).scheduleUpdateIndicesList(null)
      }
    }
  }
}