// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.*
import com.intellij.execution.util.setEmptyState
import com.intellij.execution.util.setVisibleRowCount
import com.intellij.icons.AllIcons
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.buildSystem
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.language
import com.intellij.ide.wizard.util.NewProjectLinkNewProjectWizardStep
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.ui.completion.*
import com.intellij.openapi.externalSystem.service.ui.completion.DefaultTextCompletionRenderer.Companion.append
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Cell
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil.putIfNotNull
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.text.nullize
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
import javax.swing.*

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

    private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("MavenArchetypeNewProjectWizard", 1)

    val catalogItemProperty = propertyGraph.property<MavenCatalog>(MavenCatalog.System.Internal)
    val archetypeItemProperty = propertyGraph.property(ArchetypeItem.NONE)
    val archetypeVersionProperty = propertyGraph.property("")
    val archetypeDescriptorProperty = propertyGraph.property(emptyMap<String, String>())

    var catalogItem by catalogItemProperty
    var archetypeItem by archetypeItemProperty
    var archetypeVersion by archetypeVersionProperty
    var archetypeDescriptor by archetypeDescriptorProperty

    private lateinit var catalogComboBox: ComboBox<MavenCatalog>
    private lateinit var archetypeComboBox: TextCompletionComboBox<ArchetypeItem>
    private lateinit var archetypeVersionComboBox: TextCompletionComboBox<String>
    private lateinit var archetypeDescriptorTable: PropertiesTable

    init {
      catalogItemProperty.afterChange { reloadArchetypes() }
      archetypeItemProperty.afterChange { reloadArchetypeVersions() }
      archetypeVersionProperty.afterChange { reloadArchetypeDescriptor() }
    }

    override fun setupSettingsUI(builder: Panel) {
      super.setupSettingsUI(builder)
      with(builder) {
        row {
          layout(RowLayout.LABEL_ALIGNED)
          catalogComboBox = ComboBox(CollectionComboBoxModel())
          label(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label"))
            .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
            .applyToComponent { icon = AllIcons.General.ContextHelp }
            .applyToComponent { toolTipText = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.tooltip") }
          cell(catalogComboBox)
            .applyToComponent { renderer = CatalogRenderer() }
            .applyToComponent { setSwingPopup(false) }
            .bindItem(catalogItemProperty)
            .columns(COLUMNS_MEDIUM)
          link(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.manage.button")) {
            manageCatalogs()
          }
        }.topGap(TopGap.SMALL)
        row {
          layout(RowLayout.LABEL_ALIGNED)
          archetypeComboBox = TextCompletionComboBox(context.project, ArchetypeConverter())
          label(MavenWizardBundle.message("maven.new.project.wizard.archetype.label"))
            .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
            .applyToComponent { icon = AllIcons.General.ContextHelp }
            .applyToComponent { toolTipText = MavenWizardBundle.message("maven.new.project.wizard.archetype.tooltip") }
          cell(archetypeComboBox)
            .applyToComponent { bindSelectedItem(archetypeItemProperty) }
            .horizontalAlign(HorizontalAlign.FILL)
            .resizableColumn()
            .validationOnApply { validateArchetypeId() }
          button(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.button")) {
            addArchetype()
          }
        }.topGap(TopGap.SMALL)
        row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
          archetypeVersionComboBox = TextCompletionComboBox(context.project, ArchetypeVersionConverter())
          cell(archetypeVersionComboBox)
            .applyToComponent { bindSelectedItem(archetypeVersionProperty) }
            .validationOnApply { validateArchetypeVersion() }
            .columns(10)
        }.topGap(TopGap.SMALL)
        group(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.title")) {
          row {
            archetypeDescriptorTable = PropertiesTable()
              .setVisibleRowCount(3)
              .setEmptyState(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.empty"))
              .bindProperties(archetypeDescriptorProperty.transform(
                { it.map { (k, v) -> PropertiesTable.Property(k, v) } },
                { it.associate { (n, v) -> n to v } }
              ))
            cell(archetypeDescriptorTable.component)
              .horizontalAlign(HorizontalAlign.FILL)
              .verticalAlign(VerticalAlign.FILL)
              .resizableColumn()
          }.resizableRow()
        }.resizableRow()
      }
      reloadCatalogs()
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      super.setupAdvancedSettingsUI(builder)
      with(builder) {
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
          textField()
            .bindText(versionProperty)
            .columns(COLUMNS_MEDIUM)
            .validationOnInput { validateVersion() }
            .validationOnApply { validateVersion() }
        }.bottomGap(BottomGap.SMALL)
      }
    }

    fun ValidationInfoBuilder.validateArchetypeId(): ValidationInfo? {
      val isEmptyGroupId = archetypeItem.groupId.isEmpty()
      val isEmptyArtifactId = archetypeItem.artifactId.isEmpty()
      if (isEmptyGroupId && isEmptyArtifactId) {
        val message = MavenWizardBundle.message("maven.new.project.wizard.archetype.error.empty")
        Messages.showErrorDialog(archetypeVersionComboBox, message)
        return error(message)
      }
      if (isEmptyGroupId) {
        val message = MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.error.empty")
        Messages.showErrorDialog(archetypeVersionComboBox, message)
        return error(message)
      }
      if (isEmptyArtifactId) {
        val message = MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.error.empty")
        Messages.showErrorDialog(archetypeVersionComboBox, message)
        return error(message)
      }
      return null
    }

    fun ValidationInfoBuilder.validateArchetypeVersion(): ValidationInfo? {
      if (archetypeVersion.isEmpty()) {
        val message = MavenWizardBundle.message("maven.new.project.wizard.archetype.version.error.empty")
        Messages.showErrorDialog(archetypeVersionComboBox, message)
        return error(message)
      }
      return null
    }

    private fun <R> Component.executeBackgroundTask(onBackgroundThread: () -> R, onUiThread: (R) -> Unit) {
      BackgroundTaskUtil.execute(backgroundExecutor, context.disposable) {
        val result = onBackgroundThread()
        invokeLater(ModalityState.stateForComponent(this)) {
          onUiThread(result)
        }
      }
    }

    private fun Component.invokeWhenBackgroundTasksFinished(onUiThread: () -> Unit) {
      /** [backgroundExecutor] has one worker, so we can schedule NOP task and call callback when it completed. */
      executeBackgroundTask(onBackgroundThread = /*NOP*/{}, onUiThread = { onUiThread() })
    }

    private fun reloadCatalogs() {
      val catalogManager = MavenCatalogManager.getInstance()
      val catalogs = catalogManager.getCatalogs(context.projectOrDefault)
      val oldCatalogs = catalogComboBox.collectionModel.items
      val addedCatalogs = catalogs.toSet() - oldCatalogs.toSet()

      catalogComboBox.collectionModel.replaceAll(catalogs)
      when {
        addedCatalogs.isNotEmpty() ->
          catalogItem = addedCatalogs.first()
        catalogItem !in catalogs ->
          catalogItem = catalogs.firstOrNull() ?: MavenCatalog.System.Internal
      }
    }

    private fun manageCatalogs() {
      val dialog = MavenManageCatalogsDialog(context.projectOrDefault)
      if (dialog.showAndGet()) {
        reloadCatalogs()
      }
    }

    private fun reloadArchetypes() {
      val archetypeManager = MavenArchetypeManager.getInstance(context.projectOrDefault)

      archetypeComboBox.collectionModel.removeAll()
      archetypeItem = ArchetypeItem.NONE
      archetypeComboBox.executeBackgroundTask(
        onBackgroundThread = {
          archetypeManager.getArchetypes(catalogItem)
            .groupBy { ArchetypeItem.Id(it) }
            .map { ArchetypeItem(it.value) }
            .naturalSorted()
        },
        onUiThread = { archetypes ->
          archetypeComboBox.collectionModel.replaceAll(archetypes)
          archetypeItem = ArchetypeItem.NONE
        }
      )
    }

    private fun addArchetype() {
      val dialog = MavenAddArchetypeDialog(context.projectOrDefault)
      if (dialog.showAndGet()) {
        val catalog = dialog.getCatalog()
        val groupId = dialog.archetypeGroupId
        val artifactId = dialog.archetypeArtifactId
        val version = dialog.archetypeVersion

        if (catalog != null) {
          catalogComboBox.collectionModel.add(catalog)
        }
        catalogItem = catalog ?: MavenCatalog.System.Internal
        archetypeComboBox.invokeWhenBackgroundTasksFinished {
          archetypeComboBox.text = "$groupId:$artifactId"
          archetypeVersionComboBox.invokeWhenBackgroundTasksFinished {
            archetypeVersionComboBox.text = version
          }
        }
      }
    }

    private fun reloadArchetypeVersions() {
      val versions = archetypeItem.versions.naturalSorted().reversed()
      archetypeVersionComboBox.collectionModel.replaceAll(versions)
      archetypeVersion = versions.firstOrNull() ?: ""
    }

    private fun reloadArchetypeDescriptor() {
      archetypeDescriptor = emptyMap()
      archetypeDescriptorTable.tableView.executeBackgroundTask(
        onBackgroundThread = { resolveArchetypeDescriptor() },
        onUiThread = { archetypeDescriptor = it }
      )
    }

    private fun resolveArchetypeDescriptor(): Map<String, String> {
      val archetypeManager = MavenArchetypeManager.getInstance(context.projectOrDefault)
      val catalog = catalogItem.location
      val groupId = archetypeItem.groupId.nullize() ?: return emptyMap()
      val artifactId = archetypeItem.artifactId.nullize() ?: return emptyMap()
      val version = archetypeVersion.nullize() ?: return emptyMap()
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
          archetypeItem.groupId,
          archetypeItem.artifactId,
          archetypeVersion,
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

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as ArchetypeItem

      return Id(this) == Id(other)
    }

    override fun hashCode() = Id(this).hashCode()

    data class Id(val groupId: String, val artifactId: String) {
      constructor(item: ArchetypeItem) : this(item.groupId, item.artifactId)
      constructor(archetype: MavenArchetype) : this(archetype.groupId, archetype.artifactId)
    }

    companion object {
      val NONE = ArchetypeItem("", "", listOf())
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

  private class ArchetypeConverter : TextCompletionComboBoxConverter<ArchetypeItem> {
    override fun getItem(text: String) =
      text.nullize(true)?.let {
        ArchetypeItem(
          groupId = text.substringBefore(':'),
          artifactId = text.substringAfter(':', ""),
          versions = emptyList()
        )
      } ?: ArchetypeItem.NONE

    override fun getText(item: ArchetypeItem) =
      item.run {
        if (artifactId.isNotEmpty())
          "$groupId:$artifactId"
        else
          groupId
      }

    override fun customizeCellRenderer(editor: TextCompletionField<ArchetypeItem>, cell: Cell<ArchetypeItem>) {
      val item = cell.item
      val text = editor.getTextToComplete()
      with(cell.component) {
        val groupIdSuffix = text.substringBefore(':')
        val artifactIdPrefix = text.substringAfter(':', "")
        if (':' in text && item.groupId.endsWith(groupIdSuffix) && item.artifactId.startsWith(artifactIdPrefix)) {
          val groupIdPrefix = item.groupId.removeSuffix(groupIdSuffix)
          val artifactIdSuffix = item.artifactId.removePrefix(artifactIdPrefix)
          append(groupIdPrefix, GRAYED_ATTRIBUTES, text, REGULAR_MATCHED_ATTRIBUTES)
          append(groupIdSuffix, REGULAR_MATCHED_ATTRIBUTES)
          if (item.artifactId.isNotEmpty()) {
            append(":", REGULAR_MATCHED_ATTRIBUTES)
            append(artifactIdPrefix, REGULAR_MATCHED_ATTRIBUTES)
            append(artifactIdSuffix, REGULAR_ATTRIBUTES, text, REGULAR_MATCHED_ATTRIBUTES)
          }
        }
        else {
          append(item.groupId, GRAYED_ATTRIBUTES, text, REGULAR_MATCHED_ATTRIBUTES)
          if (item.artifactId.isNotEmpty()) {
            append(":", GRAYED_ATTRIBUTES)
            append(item.artifactId, REGULAR_ATTRIBUTES, text, REGULAR_MATCHED_ATTRIBUTES)
          }
        }
      }
    }
  }

  private class ArchetypeVersionConverter : TextCompletionComboBoxConverter<@NlsSafe String> {
    override fun getItem(text: String) = text.trim()

    override fun getText(item: String) = item

    override fun customizeCellRenderer(editor: TextCompletionField<String>, cell: Cell<@NlsSafe String>) {
      cell.component.append(cell.item, editor.getTextToComplete())
    }
  }

  class Builder : GeneratorNewProjectWizardBuilderAdapter(MavenArchetypeNewProjectWizard())
}