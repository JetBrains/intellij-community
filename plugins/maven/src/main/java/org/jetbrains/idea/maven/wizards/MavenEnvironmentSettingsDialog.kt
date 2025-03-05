// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.bindEmptyText
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import org.jetbrains.idea.maven.project.BundledMaven3
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectBundle.message
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType
import org.jetbrains.idea.maven.utils.MavenUtil
import kotlin.io.path.pathString

class MavenEnvironmentSettingsDialog(private val project: Project,
                                     private val settings: MavenGeneralSettings,
                                     private val runImportAfter: Runnable) : DialogWrapper(project) {

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


  override fun doOKAction() {
    super.doOKAction()
    runImportAfter.run()
  }

  override fun doCancelAction() {
    super.doCancelAction()
    runImportAfter.run()
  }

  private fun resolveDefaultUserSettingsFile(): String {
    return MavenUtil.resolveUserSettingsPath("", project).pathString
  }

  private fun resolveDefaultLocalRepository(): String {
    val mavenHomeType = settings.mavenHomeType.let { it as? StaticResolvedMavenHomeType } ?: BundledMaven3
    return MavenUtil.resolveLocalRepository(project, "", mavenHomeType, userSettingsProperty.get()).pathString
  }

  override fun createActions() = arrayOf(okAction)

  override fun createCenterPanel() = panel {
    row(MavenConfigurableBundle.message("maven.settings.environment.user.settings") + ":") {
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle(message("maven.select.maven.settings.file"))
      textFieldWithBrowseButton(fileChooserDescriptor, project)
        .bindText(userSettingsProperty)
        .applyToComponent { bindEmptyText(defaultUserSettingsProperty.toUiPathProperty()) }
        .align(AlignX.FILL)
        .columns(COLUMNS_MEDIUM)
    }
    row(MavenConfigurableBundle.message("maven.settings.environment.local.repository") + ":") {
      val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(message("maven.select.local.repository"))
      textFieldWithBrowseButton(fileChooserDescriptor, project)
        .bindText(localRepositoryProperty)
        .applyToComponent { bindEmptyText(defaultLocalRepositoryProperty.toUiPathProperty()) }
        .align(AlignX.FILL)
        .columns(COLUMNS_MEDIUM)
    }
    onApply {
      settings.setUserSettingsFile(userSettingsProperty.get())
      settings.setLocalRepository(localRepositoryProperty.get())
    }
  }
}
