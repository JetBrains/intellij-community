// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.*
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenUtil
import javax.swing.JList

class MavenJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {
  override val name = MavenUtil.SYSTEM_ID.readableName

  override fun createStep(parent: JavaNewProjectWizard.Step) = Step(parent)

  class Step(parent: JavaNewProjectWizard.Step) : MavenNewProjectWizardStep<JavaNewProjectWizard.Step>(parent) {

    private val archetypeProperty = propertyGraph.graphProperty<MavenArchetype?> { null }

    override fun setupAdvancedSettingsUI(panel: Panel) {
      super.setupAdvancedSettingsUI(panel)
      with(panel) {
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.label")) {
          val archetypes = CollectionComboBoxModel<MavenArchetype?>()
          val comboBox = comboBox(archetypes, ArchetypeRenderer())
            .applyToComponent { setSwingPopup(false) }
            .bindItem(archetypeProperty)
            .columns(COLUMNS_MEDIUM)
          loadArchetypes(archetypes)
          button(MavenWizardBundle.message("maven.new.project.wizard.add.archetype.button")) {
            addArchetype(comboBox.component, archetypes)
          }
        }
      }
    }

    private fun loadArchetypes(archetypes: CollectionComboBoxModel<MavenArchetype?>) {
      archetypes.add(null)
      val indicesManager = getIndicesManager()
      ApplicationManager.getApplication().executeOnPooledThread {
        archetypes.add(indicesManager.archetypes.sortedBy { it.groupId })
      }
    }

    private fun addArchetype(comboBox: ComboBox<MavenArchetype?>, archetypes: CollectionComboBoxModel<MavenArchetype?>) {
      val dialog = MavenAddArchetypeDialog(comboBox)
      if (dialog.showAndGet()) {
        val archetype = dialog.archetype
        archetypes.add(archetype)
        comboBox.selectedItem = archetype
        addArchetypeIntoIndices(archetype)
      }
    }

    private fun addArchetypeIntoIndices(archetype: MavenArchetype) {
      val indicesManager = getIndicesManager()
      ApplicationManager.getApplication().executeOnPooledThread {
        indicesManager.addArchetype(archetype)
      }
    }

    private fun getIndicesManager(): MavenIndicesManager {
      val project = context.project ?: ProjectManager.getInstance().defaultProject
      return MavenIndicesManager.getInstance(project)
    }

    override fun setupProject(project: Project) {
      val builder = InternalMavenModuleBuilder().apply {
        moduleJdk = sdk
        name = parentStep.name
        contentEntryPath = parentStep.projectPath.systemIndependentPath

        parentProject = parentData
        aggregatorProject = parentData
        projectId = MavenId(groupId, artifactId, version)
        isInheritGroupId = parentData?.mavenId?.groupId == groupId
        isInheritVersion = parentData?.mavenId?.version == version

        archetype = archetypeProperty.get()
        propertiesToCreateByArtifact = LinkedHashMap<String, String>().apply {
          put("groupId", groupId)
          put("artifactId", artifactId)
          put("version", version)

          archetypeProperty.get()?.let { archetype ->
            put("archetypeGroupId", archetype.groupId)
            put("archetypeArtifactId", archetype.artifactId)
            put("archetypeVersion", archetype.version)
            if (archetype.repository != null) {
              put("archetypeRepository", archetype.repository)
            }
          }
        }
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  private class ArchetypeRenderer() : ColoredListCellRenderer<MavenArchetype?>() {
    override fun customizeCellRenderer(
      list: JList<out MavenArchetype?>,
      value: MavenArchetype?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      if (value == null) {
        append(MavenWizardBundle.message("maven.new.project.wizard.no.archetype.label"))
      }
      else if (index == -1) {
        append(value.artifactId + ":" + value.version)
      }
      else {
        append(value.groupId + ":", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(value.artifactId + ":" + value.version)
      }
    }
  }
}