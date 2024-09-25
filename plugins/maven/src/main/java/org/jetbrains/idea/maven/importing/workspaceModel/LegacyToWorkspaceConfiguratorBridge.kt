// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.project.MavenProject
import java.util.stream.Stream
import kotlin.streams.asStream

sealed class LegacyToWorkspaceConfiguratorBridge : MavenWorkspaceConfigurator {

  override fun getAdditionalFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<MavenWorkspaceConfigurator.AdditionalFolder> {
    val result = ArrayList<MavenWorkspaceConfigurator.AdditionalFolder>()
    legacyImporters(context.mavenProject).forEach { importer ->
      importer.collectSourceRoots(context.mavenProject) { path, type ->
        result.add(MavenWorkspaceConfigurator.AdditionalFolder(path, type.toFolderType()))
      }
    }
    return result.asSequence().asStream()
  }

  override fun getFoldersToExclude(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
    val result = ArrayList<String>()
    legacyImporters(context.mavenProject).forEach { it.collectExcludedFolders(context.mavenProject, result) }
    return result.asSequence().asStream()
  }

  abstract fun legacyImporters(mavenProject: MavenProject): List<MavenImporter>
}