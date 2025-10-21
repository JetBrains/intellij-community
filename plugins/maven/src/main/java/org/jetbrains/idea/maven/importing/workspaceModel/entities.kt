// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity

object MavenEntitySource : EntitySource

interface MavenProjectsTreeSettingsEntity: WorkspaceEntity {
  val importedFilePaths: List<String>
}
