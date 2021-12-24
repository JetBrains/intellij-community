// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.buildSystem
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.language
import com.intellij.ide.wizard.util.NewProjectLinkNewProjectWizardStep
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
import icons.OpenapiIcons
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import javax.swing.Icon
import javax.swing.JList

class MavenArchetypeNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = javaClass.name

  override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.name")

  override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo

  override fun createStep(context: WizardContext): NewProjectWizardStep {
    return RootNewProjectWizardStep(context).chain(::CommentStep, ::NewProjectWizardBaseStep, ::Step)
  }

  private class CommentStep(parent: NewProjectWizardStep) : NewProjectLinkNewProjectWizardStep(parent) {
    override fun getComment(name: String): String {
      return MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.comment", context.isCreatingNewProjectInt, name)
    }

    override fun onStepSelected(step: NewProjectWizardStep) {
      step.language = JavaNewProjectWizard.JAVA
      step.buildSystem = MavenJavaNewProjectWizard.MAVEN
    }
  }

  private class Step(parent: NewProjectWizardBaseStep) : MavenNewProjectWizardStep<NewProjectWizardBaseStep>(parent) {

    val archetypeProperty = propertyGraph.graphProperty<MavenArchetype?> { null }

    var archetype by archetypeProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.label")) {
          val archetypes = CollectionComboBoxModel<MavenArchetype?>()
          val comboBox = comboBox(archetypes, ArchetypeRenderer())
            .applyToComponent { setSwingPopup(false) }
            .bindItem(archetypeProperty)
            .columns(COLUMNS_MEDIUM)
          loadArchetypes(archetypes)
          button(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.button")) {
            addArchetype(comboBox.component, archetypes)
          }
        }.topGap(TopGap.SMALL)
      }
    }

    private fun loadArchetypes(archetypes: CollectionComboBoxModel<MavenArchetype?>) {
      val indicesManager = getIndicesManager()
      ApplicationManager.getApplication().executeOnPooledThread {
        archetypes.add(indicesManager.archetypes.sortedBy { it.groupId })
        archetype = archetypes.items.firstOrNull()
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

          archetype?.let { archetype ->
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

  private class ArchetypeRenderer : ColoredListCellRenderer<MavenArchetype?>() {
    override fun customizeCellRenderer(
      list: JList<out MavenArchetype?>,
      value: MavenArchetype?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val archetype = value ?: return
      if (index == -1) {
        append(archetype.artifactId + ":" + archetype.version)
      }
      else {
        append(archetype.groupId + ":", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(archetype.artifactId + ":" + archetype.version)
      }
    }
  }

  class Builder : GeneratorNewProjectWizardBuilderAdapter(MavenArchetypeNewProjectWizard())
}