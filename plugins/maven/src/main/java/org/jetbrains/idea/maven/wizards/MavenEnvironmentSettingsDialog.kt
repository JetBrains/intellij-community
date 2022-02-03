// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bindEmptyText
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenEnvironmentSettingsDialog(private val project: Project, private val settings: MavenGeneralSettings) : DialogWrapper(project) {

  private val propertyGraph = PropertyGraph()
  private val userSettingsProperty = propertyGraph.lazyProperty(settings::getUserSettingsFile)
  private val defaultUserSettingsProperty = propertyGraph.lazyProperty(::resolveDefaultUserSettingsFile)
  private val localRepositoryProperty = propertyGraph.lazyProperty(settings::getLocalRepository)
  private val defaultLocalRepositoryProperty = propertyGraph.lazyProperty(::resolveDefaultLocalRepository)

  init {
    title = MavenConfigurableBundle.message("maven.settings.environment.settings.title")
    init()
  }

  init {
    defaultLocalRepositoryProperty.dependsOn(userSettingsProperty) {
      resolveDefaultLocalRepository()
    }
  }

  private fun resolveDefaultUserSettingsFile(): String {
    return MavenUtil.resolveUserSettingsFile("").path
  }

  private fun resolveDefaultLocalRepository(): String {
    return MavenUtil.resolveLocalRepository("", settings.mavenHome, userSettingsProperty.get()).path
  }

  override fun createActions() = arrayOf(okAction)

  override fun createCenterPanel() = panel {
    row(MavenConfigurableBundle.message("maven.settings.environment.user.settings") + ":") {
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      val browseDialogTitle = MavenProjectBundle.message("maven.select.maven.settings.file")
      textFieldWithBrowseButton(browseDialogTitle, project, fileChooserDescriptor)
        .bindText(userSettingsProperty)
        .applyToComponent { bindEmptyText(defaultUserSettingsProperty.toUiPathProperty()) }
        .horizontalAlign(HorizontalAlign.FILL)
        .columns(COLUMNS_MEDIUM)
    }
    row(MavenConfigurableBundle.message("maven.settings.environment.local.repository") + ":") {
      val browseDialogTitle = MavenProjectBundle.message("maven.select.local.repository")
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      textFieldWithBrowseButton(browseDialogTitle, project, fileChooserDescriptor)
        .bindText(localRepositoryProperty)
        .applyToComponent { bindEmptyText(defaultLocalRepositoryProperty.toUiPathProperty()) }
        .horizontalAlign(HorizontalAlign.FILL)
        .columns(COLUMNS_MEDIUM)
    }
    onApply {
      settings.setUserSettingsFile(userSettingsProperty.get())
      settings.setLocalRepository(localRepositoryProperty.get())
    }
  }
}