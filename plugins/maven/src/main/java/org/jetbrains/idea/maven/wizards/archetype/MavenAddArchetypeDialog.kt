// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.ui.validation.CHECK_ARTIFACT_ID_FORMAT
import com.intellij.openapi.ui.validation.CHECK_GROUP_ID_FORMAT
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.model.MavenArchetype
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

  fun getArchetype(): MavenArchetype {
    return MavenArchetype(archetypeGroupId, archetypeArtifactId, archetypeVersion, catalogLocation, null)
  }

  override fun createCenterPanel() = panel {
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.label")) {
      textField()
        .bindText(archetypeGroupIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .textValidation(CHECK_NON_EMPTY, CHECK_GROUP_ID_FORMAT)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.label")) {
      textField()
        .bindText(archetypeArtifactIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .textValidation(CHECK_NON_EMPTY, CHECK_ARTIFACT_ID_FORMAT)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
      textField()
        .bindText(archetypeVersionProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .textValidation(CHECK_NON_EMPTY)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label")) {
      val title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.title")
      val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
      textFieldWithBrowseButton(title, project, descriptor)
        .bindText(catalogLocationProperty.trim())
        .applyToComponent { emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint") }
        .columns(COLUMNS_MEDIUM)
        .textValidation(CHECK_MAVEN_CATALOG)
    }
  }

  init {
    title = MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.title")
    setOKButtonText(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.add.button"))
    init()
  }
}