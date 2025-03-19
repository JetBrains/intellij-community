// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project

internal fun scheduleFoldersResolveForAllProjects(project: Project) {
  runBlockingMaybeCancellable {
    MavenFolderResolver(project).resolveFoldersAndImport()
  }
}