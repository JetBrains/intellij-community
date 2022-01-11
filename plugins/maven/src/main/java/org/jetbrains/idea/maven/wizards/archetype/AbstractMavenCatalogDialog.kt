// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.comap
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.io.exists
import com.intellij.util.text.nullize
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.name

abstract class AbstractMavenCatalogDialog(private val project: Project) : DialogWrapper(project, true) {

  abstract fun onApply()

  private val propertyGraph = PropertyGraph()
  private val locationProperty = propertyGraph.graphProperty { "" }
  private val nameProperty = propertyGraph.graphProperty { "" }

  protected var location by locationProperty
  protected var name by nameProperty

  private fun getPathOrError() = runCatching { Path(FileUtil.expandUserHome(location)) }
  private fun getUrlOrError() = runCatching { URL(location) }

  private fun getPathOrNull() = getPathOrError().getOrNull()
  private fun getUrlOrNull() = getUrlOrError().getOrNull()

  fun getCatalog(): MavenCatalog? {
    if (MavenCatalogManager.isLocal(location)) {
      val path = getPathOrNull() ?: return null
      return MavenCatalog.Local(name, path)
    }
    else {
      val url = getUrlOrNull() ?: return null
      return MavenCatalog.Remote(name, url)
    }
  }

  private fun suggestNameByLocation(): String {
    if (MavenCatalogManager.isLocal(location)) {
      return getPathOrNull()?.name?.nullize() ?: location
    }
    else {
      return getUrlOrNull()?.host?.nullize() ?: location
    }
  }

  private fun ValidationInfoBuilder.validateLocation(): ValidationInfo? {
    if (location.isEmpty()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.empty"))
    }
    if (MavenCatalogManager.isLocal(location)) {
      return validateLocalLocation()
    }
    else {
      return validateRemoteLocation()
    }
  }

  private fun ValidationInfoBuilder.validateLocalLocation(): ValidationInfo? {
    val pathOrError = getPathOrError()
    val exception = pathOrError.exceptionOrNull()
    if (exception != null) {
      val message = exception.message
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message))
    }
    val path = pathOrError.getOrThrow()
    if (!path.exists()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.not.exists"))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateRemoteLocation(): ValidationInfo? {
    val exception = getUrlOrError().exceptionOrNull()
    if (exception != null) {
      val message = exception.message
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.error.invalid", message))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateName(): ValidationInfo? {
    if (name.isEmpty()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.name.error.empty"))
    }
    return null
  }

  override fun createCenterPanel() = panel {
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.label")) {
      val title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.title")
      val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      textFieldWithBrowseButton(title, project, descriptor)
        .bindText(locationProperty.comap { it.trim() })
        .applyToComponent {
          emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint")
        }
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateLocation() }
        .validationOnApply { validateLocation() }
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.name.label")) {
      textField()
        .bindText(nameProperty.comap { it.trim() })
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateName() }
        .validationOnApply { validateName() }
    }
    onApply { onApply() }
  }

  init {
    nameProperty.dependsOn(locationProperty, ::suggestNameByLocation)
  }
}