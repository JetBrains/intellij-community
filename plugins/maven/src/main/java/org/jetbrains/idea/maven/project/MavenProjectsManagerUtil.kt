// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.utils.runBlockingCancellableUnderIndicator

internal fun scheduleFoldersResolveForAllProjects(project: Project) {
  runBlockingCancellableUnderIndicator {
    MavenFolderResolver(project).resolveFoldersAndImport()
  }
}