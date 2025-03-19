// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.ui.dsl.builder.*
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.idea.maven.wizards.MavenWizardBundle.message

abstract class AbstractMavenCatalogDialog(private val project: Project) : DialogWrapper(project, true) {

  abstract fun onApply()

  private val propertyGraph = PropertyGraph()
  private val locationProperty = propertyGraph.property("")
  private val nameProperty = propertyGraph.property("")

  protected var location by locationProperty
  protected var name by nameProperty

  fun getCatalog(): MavenCatalog? {
    if (location.isNotEmpty()) {
      return createCatalog(name, location)
    }
    return null
  }

  override fun createCenterPanel() = panel {
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.label")) {
      val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor().withTitle(message("maven.new.project.wizard.archetype.catalog.dialog.location.title"))
      textFieldWithBrowseButton(descriptor, project)
        .bindText(locationProperty.trim())
        .applyToComponent { emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint") }
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_MAVEN_CATALOG)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.name.label")) {
      textField()
        .bindText(nameProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY)
    }
    onApply { onApply() }
  }

  init {
    nameProperty.dependsOn(locationProperty) { suggestCatalogNameByLocation(location) }
  }
}
