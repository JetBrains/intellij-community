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
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.collectionModel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil.putIfNotNull
import com.intellij.util.io.systemIndependentPath
import icons.OpenapiIcons
import org.jetbrains.idea.maven.indices.MavenArchetypeManager
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.wizards.InternalMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizard
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
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
    val archetypeDescriptorProperty = propertyGraph.graphProperty<Map<String, String>> { emptyMap() }

    var catalogItem by catalogItemProperty
    var archetypeItem by archetypeItemProperty
    var archetypeVersion by archetypeVersionProperty
    var archetypeDescriptor by archetypeDescriptorProperty

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label")) {
          val comboBox = comboBox(CollectionComboBoxModel(), CatalogRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { loadCatalogs() }
            .bindItem(catalogItemProperty)
            .columns(COLUMNS_MEDIUM)
          button(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.add.button")) {
            comboBox.component.addCatalog()
          }
        }.topGap(TopGap.SMALL)
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.label")) {
          comboBox(CollectionComboBoxModel(), ArchetypeRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { reloadArchetypes() }
            .applyToComponent { catalogItemProperty.afterChange { reloadArchetypes() } }
            .bindItem(archetypeItemProperty)
            .horizontalAlign(HorizontalAlign.FILL)
        }.topGap(TopGap.SMALL)
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
          comboBox(CollectionComboBoxModel(), ArchetypeVersionRenderer())
            .applyToComponent { setSwingPopup(false) }
            .applyToComponent { reloadArchetypeVersions() }
            .applyToComponent { archetypeItemProperty.afterChange { reloadArchetypeVersions() } }
            .bindItem(archetypeVersionProperty)
            .columns(10)
        }.topGap(TopGap.SMALL)
        group(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.title")) {
          row {
            val table = PropertiesTable()
              .setVisibleRowCount(3)
              .setEmptyState(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.empty"))
              .apply { reloadArchetypeDescriptor() }
              .apply { archetypeVersionProperty.afterChange { reloadArchetypeDescriptor() } }
              .bindProperties(archetypeDescriptorProperty.transform(
                { it.map { (k, v) -> PropertiesTable.Property(k, v) } },
                { it.associate { (n, v) -> n to v } }
              ))
            cell(table.component)
              .horizontalAlign(HorizontalAlign.FILL)
          }
        }
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
      val dialog = MavenAddCatalogDialog(context.projectOrDefault)
      if (dialog.showAndGet()) {
        val catalog = dialog.getCatalog() ?: return
        collectionModel.add(catalog)
        catalogItem = catalog
      }
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

    private fun ComboBox<String>.reloadArchetypeVersions() {
      val versions = archetypeItem?.versions ?: emptyList()
      collectionModel.replaceAll(versions)
      archetypeVersion = versions.firstOrNull()
    }

    private fun PropertiesTable.reloadArchetypeDescriptor() {
      archetypeDescriptor = emptyMap()
      tableView.executeBackgroundTask(
        onBackgroundThread = { resolveArchetypeDescriptor() },
        onUiThread = { archetypeDescriptor = it }
      )
    }

    private fun resolveArchetypeDescriptor(): Map<String, String> {
      val archetypeManager = MavenArchetypeManager.getInstance(context.projectOrDefault)
      val catalog = catalogItem.location
      val groupId = archetypeItem?.groupId ?: return emptyMap()
      val artifactId = archetypeItem?.artifactId ?: return emptyMap()
      val version = archetypeVersion ?: return emptyMap()
      val descriptor = archetypeManager.resolveAndGetArchetypeDescriptor(groupId, artifactId, version, catalog) ?: return emptyMap()
      return descriptor.toMutableMap()
        .apply { remove("groupId") }
        .apply { remove("artifactId") }
        .apply { remove("version") }
        .apply { remove("archetypeGroupId") }
        .apply { remove("archetypeArtifactId") }
        .apply { remove("archetypeVersion") }
        .apply { remove("archetypeRepository") }
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
          catalogItem.location,
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
          putAll(archetypeDescriptor)
        }
      }

      ExternalProjectsManagerImpl.setupCreatedProject(project)
      builder.commit(project)
    }
  }

  private class ArchetypeItem(
    val groupId: @NlsSafe String,
    val artifactId: @NlsSafe String,
    val versions: List<@NlsSafe String>
  ) {
    constructor(archetype: MavenArchetype, versions: List<String>) :
      this(archetype.groupId, archetype.artifactId, versions)

    constructor(archetypes: List<MavenArchetype>) :
      this(archetypes.first(), archetypes.map { it.version })

    override fun toString(): String = "$groupId:$artifactId"

    class Id(val archetype: MavenArchetype) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Id

        if (archetype.groupId != other.archetype.groupId) return false
        if (archetype.artifactId != other.archetype.artifactId) return false

        return true
      }

      override fun hashCode(): Int {
        var result = archetype.groupId.hashCode()
        result = 31 * result + archetype.artifactId.hashCode()
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
      append(archetype.groupId + ":", SimpleTextAttributes.GRAYED_ATTRIBUTES)
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