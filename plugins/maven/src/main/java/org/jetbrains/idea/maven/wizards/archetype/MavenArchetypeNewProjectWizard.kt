// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.Companion.REGULAR_MATCHED_ATTRIBUTES
import com.intellij.execution.util.setEmptyState
import com.intellij.execution.util.setVisibleRowCount
import com.intellij.icons.AllIcons
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.comment.LinkNewProjectWizardStep
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBox
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBoxConverter
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Cell
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Companion.append
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable
import com.intellij.openapi.externalSystem.service.ui.spinner.ComponentSpinnerExtension.Companion.setSpinning
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.collectionModel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.text.nullize
import com.intellij.util.ui.update.UiNotifyConnector
import icons.OpenapiIcons
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.wizards.MavenJavaModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

internal class MavenArchetypeNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = "MavenArchetype"

  override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.name")

  override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo

  override fun createStep(context: WizardContext): NewProjectWizardStep =
    RootNewProjectWizardStep(context)
      .nextStep(::CommentStep)
      .nextStep(::newProjectWizardBaseStepWithoutGap)
      .nextStep(::GitNewProjectWizardStep)
      .nextStep(::Step)
      .nextStep(::AssetsStep)

  private class CommentStep(parent: NewProjectWizardStep) : LinkNewProjectWizardStep(parent) {

    override val builderId: String =
      BaseLanguageGeneratorNewProjectWizard.getLanguageModelBuilderId(context, JAVA)

    override val comment: String =
      MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.comment", context.isCreatingNewProjectInt, JAVA)

    override fun onStepSelected(step: NewProjectWizardStep) {
      step.javaBuildSystemData!!.buildSystem = MAVEN
    }
  }

  private class Step(
    parent: GitNewProjectWizardStep
  ) : MavenNewProjectWizardStep<GitNewProjectWizardStep>(parent),
      MavenArchetypeNewProjectWizardData {
    private var isAutoReloadArchetypeModel = true

    private val backend = MavenArchetypeNewProjectWizardBackend(context.projectOrDefault, context.disposable)

    override val catalogItemProperty = propertyGraph.property<MavenCatalog>(MavenCatalog.System.Internal)
    override var catalogItem by catalogItemProperty
    override val archetypeItemProperty = propertyGraph.property(MavenArchetypeItem.NONE)
    override var archetypeItem by archetypeItemProperty
    override val archetypeVersionProperty = propertyGraph.property("")
    override var archetypeVersion by archetypeVersionProperty
    override val archetypeDescriptorProperty = propertyGraph.property(emptyMap<String, String>())
    override var archetypeDescriptor by archetypeDescriptorProperty

    private lateinit var catalogComboBox: ComboBox<MavenCatalog>
    private lateinit var archetypeComboBox: TextCompletionComboBox<MavenArchetypeItem>
    private lateinit var archetypeVersionComboBox: TextCompletionComboBox<String>
    private lateinit var archetypeDescriptorTable: PropertiesTable
    private lateinit var archetypeDescriptorPanel: JComponent

    init {
      catalogItemProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypes() }
      archetypeItemProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypeVersions() }
      archetypeVersionProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypeDescriptor() }
      data.putUserData(MavenArchetypeNewProjectWizardData.KEY, this)
    }

    fun setupCatalogUI(builder: Panel) {
      builder.row {
        layout(RowLayout.LABEL_ALIGNED)
        catalogComboBox = ComboBox(CollectionComboBoxModel())
        label(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.label"))
          .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
          .applyToComponent { icon = AllIcons.General.ContextHelp }
          .applyToComponent { toolTipText = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.tooltip") }
          .applyToComponent { labelFor = catalogComboBox }
        cell(catalogComboBox)
          .applyToComponent { renderer = CatalogRenderer() }
          .applyToComponent { setSwingPopup(false) }
          .bindItem(catalogItemProperty)
          .columns(COLUMNS_MEDIUM)
          .gap(RightGap.SMALL)
        link(MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.manage.button")) {
          manageCatalogs()
        }
      }.bottomGap(BottomGap.SMALL)
    }

    fun setupArchetypeUI(builder: Panel) {
      builder.row {
        layout(RowLayout.LABEL_ALIGNED)
        archetypeComboBox = TextCompletionComboBox(context.project, ArchetypeConverter())
        label(MavenWizardBundle.message("maven.new.project.wizard.archetype.label"))
          .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
          .applyToComponent { icon = AllIcons.General.ContextHelp }
          .applyToComponent { toolTipText = MavenWizardBundle.message("maven.new.project.wizard.archetype.tooltip") }
          .applyToComponent { labelFor = archetypeComboBox }
        cell(archetypeComboBox)
          .applyToComponent { bindSelectedItem(archetypeItemProperty) }
          .align(AlignX.FILL)
          .resizableColumn()
          .validationOnApply { validateArchetypeId() }
          .gap(RightGap.SMALL)
        button(MavenWizardBundle.message("maven.new.project.wizard.archetype.add.button")) {
          addArchetype()
        }
      }.bottomGap(BottomGap.SMALL)
    }

    fun setupArchetypeVersionUI(builder: Panel) {
      builder.row(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.label")) {
        archetypeVersionComboBox = TextCompletionComboBox(context.project, ArchetypeVersionConverter())
        cell(archetypeVersionComboBox)
          .applyToComponent { bindSelectedItem(archetypeVersionProperty) }
          .validationOnApply { validateArchetypeVersion() }
          .columns(10)
      }.bottomGap(BottomGap.SMALL)
    }

    fun setupArchetypeDescriptorUI(builder: Panel) {
      builder.group(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.title")) {
        row {
          archetypeDescriptorTable = PropertiesTable()
            .setVisibleRowCount(3)
            .setEmptyState(MavenWizardBundle.message("maven.new.project.wizard.archetype.properties.empty"))
            .bindProperties(archetypeDescriptorProperty.transform(
              { it.map { (k, v) -> PropertiesTable.Property(k, v) } },
              { it.associate { (n, v) -> n to v } }
            ))
          archetypeDescriptorPanel = archetypeDescriptorTable.component
          cell(archetypeDescriptorPanel)
            .align(Align.FILL)
            .resizableColumn()
        }.resizableRow()
      }.resizableRow()
    }

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupParentsUI(builder)
      setupCatalogUI(builder)
      setupArchetypeUI(builder)
      setupArchetypeVersionUI(builder)
      setupArchetypeDescriptorUI(builder)
      UiNotifyConnector.doWhenFirstShown(catalogComboBox) { reloadCatalogs() }
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
      setupVersionUI(builder)
    }

    fun ValidationInfoBuilder.validateArchetypeId(): ValidationInfo? {
      val isEmptyGroupId = archetypeItem.groupId.isEmpty()
      val isEmptyArtifactId = archetypeItem.artifactId.isEmpty()
      if (isEmptyGroupId && isEmptyArtifactId) {
        return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.error.empty"))
      }
      if (isEmptyGroupId) {
        return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.group.id.error.empty"))
      }
      if (isEmptyArtifactId) {
        return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.artifact.id.error.empty"))
      }
      return null
    }

    fun ValidationInfoBuilder.validateArchetypeVersion(): ValidationInfo? {
      if (archetypeVersion.isEmpty()) {
        return error(MavenWizardBundle.message("maven.new.project.wizard.archetype.version.error.empty"))
      }
      return null
    }

    private fun reloadCatalogs() {
      val catalogs = backend.getCatalogs()
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
      archetypeComboBox.setSpinning(true)
      archetypeComboBox.collectionModel.removeAll()
      archetypeItem = MavenArchetypeItem.NONE
      backend.collectArchetypeIds(archetypeComboBox, catalogItem) { archetypes ->
        archetypeComboBox.setSpinning(false)
        archetypeComboBox.collectionModel.replaceAll(archetypes)
        archetypeItem = MavenArchetypeItem.NONE
      }
    }

    private fun addArchetype() {
      val dialog = MavenAddArchetypeDialog(context.projectOrDefault)
      if (dialog.showAndGet()) {
        setArchetype(dialog.getArchetype())
      }
    }

    private fun findOrAddCatalog(catalogLocation: String?): MavenCatalog? {
      var catalog = catalogComboBox.collectionModel.items
        .find { it.location == catalogLocation }
      if (catalogLocation != null && catalog == null) {
        catalog = createCatalog(catalogLocation)
        catalogComboBox.collectionModel.add(catalog)
      }
      return catalog
    }

    private fun setArchetype(archetype: MavenArchetype) {
      withDisableAutoReloadArchetypeModel {
        archetypeComboBox.setSpinning(true)

        catalogItem = findOrAddCatalog(archetype.repository) ?: MavenCatalog.System.Internal
        archetypeItem = MavenArchetypeItem(archetype.groupId, archetype.artifactId)
        archetypeVersion = archetype.version

        archetypeComboBox.collectionModel.removeAll()
        archetypeVersionComboBox.collectionModel.removeAll()
        archetypeDescriptor = emptyMap()

        backend.collectArchetypeIds(archetypeComboBox, catalogItem) { archetypes ->
          archetypeComboBox.setSpinning(false)
          withDisableAutoReloadArchetypeModel {
            archetypeComboBox.collectionModel.replaceAll(archetypes)
            backend.collectArchetypeVersions(archetypeVersionComboBox, catalogItem, archetypeItem) { versions ->
              isAutoReloadArchetypeModel = false
              withDisableAutoReloadArchetypeModel {
                archetypeVersionComboBox.collectionModel.replaceAll(versions)
                backend.collectArchetypeDescriptor(archetypeDescriptorPanel, catalogItem, archetypeItem, archetypeVersion) {
                  archetypeDescriptor = it + archetypeDescriptor
                }
              }
            }
          }
        }
      }
    }

    private fun <R> withDisableAutoReloadArchetypeModel(action: () -> R): R {
      isAutoReloadArchetypeModel = false
      try {
        return action()
      }
      finally {
        isAutoReloadArchetypeModel = true
      }
    }

    private fun reloadArchetypeVersions() {
      archetypeVersionComboBox.collectionModel.removeAll()
      archetypeVersion = ""
      backend.collectArchetypeVersions(archetypeVersionComboBox, catalogItem, archetypeItem) { versions ->
        archetypeVersionComboBox.collectionModel.replaceAll(versions)
        archetypeVersion = versions.firstOrNull() ?: ""
      }
    }

    private fun reloadArchetypeDescriptor() {
      archetypeDescriptor = emptyMap()
      backend.collectArchetypeDescriptor(archetypeDescriptorPanel, catalogItem, archetypeItem, archetypeVersion) {
        archetypeDescriptor = it
      }
    }

    override fun setupProject(project: Project) {
      linkMavenProject(project, MavenJavaModuleBuilder()) { builder ->
        builder.archetype = MavenArchetype(
          archetypeItem.groupId,
          archetypeItem.artifactId,
          archetypeVersion,
          catalogItem.location,
          null
        )
        builder.propertiesToCreateByArtifact = LinkedHashMap<String, String>().apply {
          put("groupId", groupId)
          put("artifactId", artifactId)
          put("version", version)
          put("archetypeGroupId", builder.archetype.groupId)
          put("archetypeArtifactId", builder.archetype.artifactId)
          put("archetypeVersion", builder.archetype.version)
          builder.archetype.repository?.let { repository ->
            put("archetypeRepository", repository)
          }
          putAll(archetypeDescriptor)
        }
      }
    }
  }

  private class CatalogRenderer : ColoredListCellRenderer<MavenCatalog>() {
    override fun customizeCellRenderer(
      list: JList<out MavenCatalog>,
      value: MavenCatalog?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      val catalog = value ?: return
      append(catalog.name)
    }
  }

  private class ArchetypeConverter : TextCompletionComboBoxConverter<MavenArchetypeItem> {
    override fun getItem(text: String) =
      text.nullize(true)?.let {
        MavenArchetypeItem(
          groupId = text.substringBefore(':'),
          artifactId = text.substringAfter(':', "")
        )
      } ?: MavenArchetypeItem.NONE

    override fun getText(item: MavenArchetypeItem) =
      item.run {
        if (artifactId.isNotEmpty())
          "$groupId:$artifactId"
        else
          groupId
      }

    override fun customizeCellRenderer(editor: TextCompletionField<MavenArchetypeItem>, cell: Cell<MavenArchetypeItem>) {
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

  class Builder : GeneratorNewProjectWizardBuilderAdapter(MavenArchetypeNewProjectWizard()) {
    override fun getWeight(): Int = JVM_WEIGHT + 100
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
      }
    }
  }
}
