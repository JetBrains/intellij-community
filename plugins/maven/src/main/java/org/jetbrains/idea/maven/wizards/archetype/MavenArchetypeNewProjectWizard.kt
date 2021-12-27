// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.buildSystem
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.language
import com.intellij.ide.wizard.util.NewProjectLinkNewProjectWizardStep
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.collectionModel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil.putIfNotNull
import com.intellij.util.io.systemIndependentPath
import icons.OpenapiIcons
import org.jetbrains.idea.maven.indices.MavenArchetypeManager
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.wizards.*
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList

class MavenArchetypeNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = javaClass.name

  override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.name")

  override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo

  override fun createStep(context: WizardContext): NewProjectWizardStep {
    return RootNewProjectWizardStep(context).chain(MavenArchetypeNewProjectWizard::CommentStep, ::NewProjectWizardBaseStep,
      MavenArchetypeNewProjectWizard::Step)
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

    private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenArchetypeNewProjectWizard", 1)

    val catalogItemProperty = propertyGraph.graphProperty<MavenCatalog> { MavenCatalog.System.Internal }
    val archetypeItemProperty = propertyGraph.graphProperty<ArchetypeItem?> { null }
    val archetypeVersionProperty = propertyGraph.graphProperty<String?> { null }

    var catalogItem by catalogItemProperty
    var archetypeItem by archetypeItemProperty
    var archetypeVersion by archetypeVersionProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label")) {
          val comboBox = comboBox(CollectionComboBoxModel(), CatalogRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { loadCatalogs() }
            .bindItem(catalogItemProperty)
            .columns(COLUMNS_MEDIUM)
          link(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.manage.link")) {
            comboBox.component.addCatalog()
          }
        }.topGap(TopGap.SMALL)
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.label")) {
          val comboBox = comboBox(CollectionComboBoxModel(), ArchetypeRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { reloadArchetypes() }
            .applyToComponent { catalogItemProperty.afterChange { reloadArchetypes() } }
            .bindItem(archetypeItemProperty)
            .columns(COLUMNS_MEDIUM)
          button(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.button")) {
            comboBox.component.addArchetype()
          }
        }.topGap(TopGap.SMALL)
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
          comboBox(CollectionComboBoxModel(), ArchetypeVersionRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { reloadArchetypeVersions() }
            .applyToComponent { archetypeItemProperty.afterChange { reloadArchetypeVersions() } }
            .bindItem(archetypeVersionProperty)
            .columns(10)
        }.topGap(TopGap.SMALL)
      }
    }

    private fun <R> Component.executeBackgroundTask(onBackgroundThread: () -> R, onUiThread: (R) -> Unit) {
      BackgroundTaskUtil.execute(backgroundExecutor, context.disposable) {
        val result = onBackgroundThread()
        invokeLater(ModalityState.stateForComponent(this)) {
          onUiThread(result)
        }
      }
    }

    private fun ComboBox<MavenCatalog>.loadCatalogs() {
      val catalogManager = MavenCatalogManager.getInstance()

      collectionModel.add(MavenCatalog.System.Internal)
      collectionModel.add(MavenCatalog.System.DefaultLocal(context.projectOrDefault))
      collectionModel.add(MavenCatalog.System.MavenCentral)
      collectionModel.add(catalogManager.getCatalogs())
    }

    private fun ComboBox<MavenCatalog>.addCatalog() {
    }

    private fun ComboBox<ArchetypeItem>.reloadArchetypes() {
      val archetypeManager = MavenArchetypeManager.getInstance(context.projectOrDefault)

      collectionModel.removeAll()
      archetypeItem = null
      executeBackgroundTask(
        onBackgroundThread = {
          archetypeManager.getArchetypes(catalogItem)
            .groupBy { ArchetypeItem.Id(it) }
            .map { ArchetypeItem(it.value) }
        },
        onUiThread = { archetypes ->
          collectionModel.replaceAll(archetypes)
          archetypeItem = archetypes.firstOrNull()
        }
      )
    }

    private fun ComboBox<ArchetypeItem>.addArchetype() {
      val dialog = MavenAddArchetypeDialog(parent)
      if (dialog.showAndGet()) {
        val archetype = dialog.archetype
        val item = ArchetypeItem(archetype)
        collectionModel.add(item)
        archetypeItem = item
      }
    }

    private fun ComboBox<String>.reloadArchetypeVersions() {
      val versions = archetypeItem?.versions ?: emptyList()
      collectionModel.replaceAll(versions)
      archetypeVersion = versions.firstOrNull()
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

        archetype = MavenArchetype(
          archetypeItem!!.groupId,
          archetypeItem!!.artifactId,
          archetypeVersion!!,
          archetypeItem!!.repository,
          null
        )
        propertiesToCreateByArtifact = LinkedHashMap<String, String>().apply {
          put("groupId", groupId)
          put("artifactId", artifactId)
          put("version", version)
          put("archetypeGroupId", archetype.groupId)
          put("archetypeArtifactId", archetype.artifactId)
          put("archetypeVersion", archetype.version)
          putIfNotNull("archetypeRepository", archetype.repository, this)
        }
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  private class ArchetypeItem(
    val groupId: @NlsSafe String,
    val artifactId: @NlsSafe String,
    val repository: @NlsSafe String?,
    val versions: List<@NlsSafe String>
  ) {
    constructor(archetype: MavenArchetype, versions: List<String>) :
      this(archetype.groupId, archetype.artifactId, archetype.repository, versions)

    constructor(archetypes: List<MavenArchetype>) :
      this(archetypes.first(), archetypes.map { it.version })

    constructor(archetype: MavenArchetype) :
      this(archetype, listOf(archetype.version))

    override fun toString(): String = "$groupId:$artifactId in $repository"

    class Id(val archetype: MavenArchetype) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Id

        if (archetype.groupId != other.archetype.groupId) return false
        if (archetype.artifactId != other.archetype.artifactId) return false
        if (archetype.repository != other.archetype.repository) return false

        return true
      }

      override fun hashCode(): Int {
        var result = archetype.groupId.hashCode()
        result = 31 * result + archetype.artifactId.hashCode()
        result = 31 * result + (archetype.repository?.hashCode() ?: 0)
        return result
      }
    }
  }

  private class CatalogRenderer : ColoredListCellRenderer<MavenCatalog>() {
    override fun customizeCellRenderer(list: JList<out MavenCatalog>,
                                       value: MavenCatalog?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      val catalog = value ?: return
      append(catalog.name)
    }
  }

  private class ArchetypeRenderer : ColoredListCellRenderer<ArchetypeItem>() {
    override fun customizeCellRenderer(
      list: JList<out ArchetypeItem>,
      value: ArchetypeItem?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val archetype = value ?: return
      if (index != -1) {
        append(archetype.groupId + ":", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      append(archetype.artifactId)
    }
  }

  private class ArchetypeVersionRenderer : ColoredListCellRenderer<@NlsSafe String>() {
    override fun customizeCellRenderer(list: JList<out @NlsSafe String>,
                                       value: @NlsSafe String?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      val version = value ?: return
      append(version)
    }
  }

  class Builder : GeneratorNewProjectWizardBuilderAdapter(MavenArchetypeNewProjectWizard())
}