// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.trim
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

class MavenAddArchetypeDialog(private val project: Project) : DialogWrapper(project, true) {

  private val propertyGraph = PropertyGraph()
  private val archetypeGroupIdProperty = propertyGraph.property("")
  private val archetypeArtifactIdProperty = propertyGraph.property("")
  private val archetypeVersionProperty = propertyGraph.property("")
  private val catalogLocationProperty = propertyGraph.property("")

  var archetypeGroupId by archetypeGroupIdProperty
  var archetypeArtifactId by archetypeArtifactIdProperty
  var archetypeVersion by archetypeVersionProperty
  var catalogLocation by catalogLocationProperty

  fun getCatalog(): MavenCatalog? {
    if (catalogLocation.isNotEmpty()) {
      val name = suggestCatalogNameByLocation(catalogLocation)
      return createCatalog(name, catalogLocation)
    }
    return null
  }

  private fun ValidationInfoBuilder.validateCatalogLocation(): ValidationInfo? {
    if (catalogLocation.isEmpty()) {
      return null
    }
    return validateCatalogLocation(catalogLocation)
  }

  private fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? {
    if (archetypeGroupId.isEmpty()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.error.empty"))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? {
    if (archetypeArtifactId.isEmpty()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.error.empty"))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateVersion(): ValidationInfo? {
    if (archetypeVersion.isEmpty()) {
      return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.error.empty"))
    }
    return null
  }

  override fun createCenterPanel() = panel {
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.label")) {
      textField()
        .bindText(archetypeGroupIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateGroupId() }
        .validationOnApply { validateGroupId() }
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.label")) {
      textField()
        .bindText(archetypeArtifactIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateArtifactId() }
        .validationOnApply { validateArtifactId() }
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
      textField()
        .bindText(archetypeVersionProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateVersion() }
        .validationOnApply { validateVersion() }
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label")) {
      val title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.title")
      val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      textFieldWithBrowseButton(title, project, descriptor)
        .bindText(catalogLocationProperty.trim())
        .applyToComponent { emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint") }
        .columns(COLUMNS_MEDIUM)
        .validationOnInput { validateCatalogLocation() }
        .validationOnApply { validateCatalogLocation() }
    }
  }

  init {
    title = MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.title")
    setOKButtonText(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.add.button"))
    init()
  }
}