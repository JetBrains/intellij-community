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
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

abstract class AbstractMavenCatalogDialog(private val project: Project) : DialogWrapper(project, true) {

  abstract fun onApply()

  private val propertyGraph = PropertyGraph()
  private val locationProperty = propertyGraph.graphProperty { "" }
  private val nameProperty = propertyGraph.graphProperty { "" }

  protected var location by locationProperty
  protected var name by nameProperty

  fun getCatalog(): MavenCatalog? {
    if (location.isNotEmpty()) {
      return createCatalog(name, location)
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
        .applyToComponent { emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint") }
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateCatalogLocation(location) }
        .validationOnApply { validateCatalogLocation(location) }
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
    nameProperty.dependsOn(locationProperty) { suggestCatalogNameByLocation(location) }
  }
}