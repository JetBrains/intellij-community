// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.groovy

import org.jetbrains.idea.maven.importing.GroovyImporter
import org.jetbrains.idea.maven.project.MavenProject

abstract class MigratedGroovyImporter(private val plugin: GroovyPluginConfigurator.KnownPlugins)
  : GroovyImporter(plugin.groupId, plugin.artifactId) {
  override fun isMigratedToConfigurator(): Boolean {
    return true
  }

  override fun isApplicable(mavenProject: MavenProject): Boolean {
    return plugin.findInProject(mavenProject) != null
  }
}