// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import org.jetbrains.idea.maven.project.MavenProject

internal class MavenModuleImportContext(
  val changedModules: List<MavenTreeModuleImportData>,
  val allModules: List<MavenTreeModuleImportData>,
  val moduleNameByProject: Map<MavenProject, String>,
  val hasChanges: Boolean
)
