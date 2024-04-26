// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.project.MavenProject

class LegacyToWorkspaceConfiguratorBridgeDynamic : LegacyToWorkspaceConfiguratorBridge() {
  override fun legacyImporters(mavenProject: MavenProject) = MavenImporter.getSuitableImporters(mavenProject, true)
}