// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.buildHelper

import org.jetbrains.idea.maven.importing.BuildHelperMavenPluginUtil
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import java.util.stream.Stream
import kotlin.streams.asStream

class MavenBuildHelperPluginConfigurator : MavenWorkspaceConfigurator {
  override fun getAdditionalFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<MavenWorkspaceConfigurator.AdditionalFolder> {
    val mavenProject = context.mavenProject
    val buildHelperPlugin = BuildHelperMavenPluginUtil.findPlugin(mavenProject)

    if (buildHelperPlugin == null) {
      return Stream.empty()
    }
    val result = ArrayList<MavenWorkspaceConfigurator.AdditionalFolder>()
    BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-source") { path ->
      result.add(MavenWorkspaceConfigurator.AdditionalFolder(path, MavenWorkspaceConfigurator.FolderType.SOURCE))
    }
    BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-resource") { path ->
      result.add(MavenWorkspaceConfigurator.AdditionalFolder(path, MavenWorkspaceConfigurator.FolderType.RESOURCE))
    }

    BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-test-source") { path ->
      result.add(MavenWorkspaceConfigurator.AdditionalFolder(path, MavenWorkspaceConfigurator.FolderType.TEST_SOURCE))
    }
    BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-test-resource") { path ->
      result.add(MavenWorkspaceConfigurator.AdditionalFolder(path, MavenWorkspaceConfigurator.FolderType.TEST_RESOURCE))
    }
    return result.asSequence().asStream()
  }
}