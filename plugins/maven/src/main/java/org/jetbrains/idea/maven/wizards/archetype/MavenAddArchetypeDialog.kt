// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.ui.validation.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

class MavenAddArchetypeDialog(private val project: Project) : DialogWrapper(project, true) {

  private val propertyGraph = PropertyGraph()
  private val archetypeGroupIdProperty = propertyGraph.property("")
  private val archetypeArtifactIdProperty = propertyGraph.property("")
  private val archetypeVersionProperty = propertyGraph.property("")
  private val catalogLocationProperty = propertyGraph.property("")

  private var archetypeGroupId by archetypeGroupIdProperty
  private var archetypeArtifactId by archetypeArtifactIdProperty
  private var archetypeVersion by archetypeVersionProperty
  private var catalogLocation by catalogLocationProperty

  fun getArchetype(): MavenArchetype {
    return MavenArchetype(archetypeGroupId, archetypeArtifactId, archetypeVersion, catalogLocation, null)
  }

  override fun createCenterPanel() = panel {
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.label")) {
      textField()
        .bindText(archetypeGroupIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_GROUP_ID)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.label")) {
      textField()
        .bindText(archetypeArtifactIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_ARTIFACT_ID)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
      textField()
        .bindText(archetypeVersionProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY)
    }
    row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label")) {
      val title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.title")
      val descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
      textFieldWithBrowseButton(title, project, descriptor)
        .bindText(catalogLocationProperty.trim())
        .applyToComponent { emptyText.text = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.dialog.location.hint") }
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_MAVEN_CATALOG)
    }
  }

  init {
    title = MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.title")
    setOKButtonText(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.dialog.add.button"))
    init()
  }
}