// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenEnvironmentSettingsDialog(private val project: Project, private val settings: MavenGeneralSettings) : DialogWrapper(project) {

  private val propertyGraph = PropertyGraph()
  private val userSettingsProperty = propertyGraph.graphProperty { settings.userSettingsFile }
  private val defaultUserSettingsProperty = propertyGraph.graphProperty { MavenUtil.resolveUserSettingsFile("").path }
  private val localRepositoryProperty = propertyGraph.graphProperty { settings.localRepository }
  private val defaultLocalRepositoryProperty = propertyGraph.graphProperty {
    MavenUtil.resolveLocalRepository("", settings.mavenHome, userSettingsProperty.get()).path
  }

  init {
    title = MavenConfigurableBundle.message("maven.settings.environment.settings.title")
    init()

    defaultLocalRepositoryProperty.dependsOn(userSettingsProperty)
    userSettingsProperty.afterChange(settings::setUserSettingsFile)
    localRepositoryProperty.afterChange(settings::setLocalRepository)
  }

  override fun createActions() = arrayOf(okAction)

  override fun createCenterPanel() = panel {
    row(MavenConfigurableBundle.message("maven.settings.environment.user.settings") + ":") {
      textFieldWithBrowseButton(
        project = project,
        property = userSettingsProperty,
        emptyTextProperty = defaultUserSettingsProperty,
        browseDialogTitle = MavenProjectBundle.message("maven.select.maven.settings.file"),
        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      )
    }
    row(MavenConfigurableBundle.message("maven.settings.environment.local.repository") + ":") {
      textFieldWithBrowseButton(
        project = project,
        property = localRepositoryProperty,
        emptyTextProperty = defaultLocalRepositoryProperty,
        browseDialogTitle = MavenProjectBundle.message("maven.select.local.repository"),
        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      )
    }
  }
}