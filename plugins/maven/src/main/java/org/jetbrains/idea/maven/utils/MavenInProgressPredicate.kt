// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.ide.observation.ActivityInProgressPredicate
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project

internal class MavenInProgressPredicate: ActivityInProgressPredicate {
  override val presentableName: String = "maven"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<MavenInProgressService>().isInProgress()
  }

}