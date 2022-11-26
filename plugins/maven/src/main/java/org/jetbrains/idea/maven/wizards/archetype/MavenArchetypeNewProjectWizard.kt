// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.REGULAR_MATCHED_ATTRIBUTES
import com.intellij.execution.util.setEmptyState
import com.intellij.execution.util.setVisibleRowCount
import com.intellij.icons.AllIcons
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.buildSystem
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.language
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.util.NewProjectLinkNewProjectWizardStep
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.ui.completion.DefaultTextCompletionRenderer.Companion.append
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBox
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionComboBoxConverter
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Cell
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable
import com.intellij.openapi.externalSystem.service.ui.spinner.ComponentSpinnerExtension.Companion.setSpinning
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.collectionModel
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.containers.ContainerUtil.putIfNotNull
import com.intellij.util.text.nullize
import com.intellij.util.ui.update.UiNotifyConnector
import icons.OpenapiIcons
import org.jetbrains.idea.maven.indices.archetype.MavenCatalog
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.InternalMavenModuleBuilder
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.idea.maven.wizards.MavenWizardBundle
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizardBackend.ArchetypeItem
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

class MavenArchetypeNewProjectWizard : GeneratorNewProjectWizard {
  override val id: String = "MavenArchetype"

  override val name: String = MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.name")

  override val icon: Icon = OpenapiIcons.RepositoryLibraryLogo

  override fun createStep(context: WizardContext) =
    RootNewProjectWizardStep(context).chain(
      ::CommentStep,
      ::newProjectWizardBaseStepWithoutGap,
      ::GitNewProjectWizardStep,
      ::Step
    ).chain(::AssetsStep)

  private class CommentStep(parent: NewProjectWizardStep) : NewProjectLinkNewProjectWizardStep(parent) {
    override fun getComment(name: String): String {
      return MavenWizardBundle.message("maven.new.project.wizard.archetype.generator.comment", context.isCreatingNewProjectInt, name)
    }

    override fun onStepSelected(step: NewProjectWizardStep) {
      step.language = JAVA
      step.buildSystem = MAVEN
    }
  }

  private class Step(parent: GitNewProjectWizardStep) : MavenNewProjectWizardStep<GitNewProjectWizardStep>(parent) {

    private var isAutoReloadArchetypeModel = true

    private val backend = MavenArchetypeNewProjectWizardBackend(context.projectOrDefault, context.disposable)

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
    private lateinit var archetypeDescriptorPanel: JComponent

    init {
      catalogItemProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypes() }
      archetypeItemProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypeVersions() }
      archetypeVersionProperty.afterChange { if (isAutoReloadArchetypeModel) reloadArchetypeDescriptor() }
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
            .gap(RightGap.SMALL)
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
            .align(AlignX.FILL)
            .resizableColumn()
            .validationOnApply { validateArchetypeId() }
            .gap(RightGap.SMALL)
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
            archetypeDescriptorPanel = archetypeDescriptorTable.component
            cell(archetypeDescriptorPanel)
              .align(Align.FILL)
              .resizableColumn()
          }.resizableRow()
        }.resizableRow()
      }
      UiNotifyConnector.doWhenFirstShown(catalogComboBox) { reloadCatalogs() }
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      super.setupAdvancedSettingsUI(builder)
      with(builder) {
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
          textField()
            .bindText(versionProperty.trim())
            .columns(COLUMNS_MEDIUM)
            .trimmedTextValidation(CHECK_NON_EMPTY)
            .whenTextChangedFromUi { logVersionChanged() }
        }.bottomGap(BottomGap.SMALL)
      }
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
      archetypeItem = ArchetypeItem.NONE
      backend.collectArchetypeIds(archetypeComboBox, catalogItem) { archetypes ->
        archetypeComboBox.setSpinning(false)
        archetypeComboBox.collectionModel.replaceAll(archetypes)
        archetypeItem = ArchetypeItem.NONE
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
        archetypeItem = ArchetypeItem(archetype.groupId, archetype.artifactId)
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
      super.setupProject(project)

      val builder = InternalMavenModuleBuilder().apply {
        moduleJdk = sdk
        name = parentStep.name
        contentEntryPath = "${parentStep.path}/${parentStep.name}"

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
      MavenProjectsManager.setupCreatedMavenProject(project)
      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)
      builder.commit(project)
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

  private class ArchetypeConverter : TextCompletionComboBoxConverter<ArchetypeItem> {
    override fun getItem(text: String) =
      text.nullize(true)?.let {
        ArchetypeItem(
          groupId = text.substringBefore(':'),
          artifactId = text.substringAfter(':', "")
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

  class Builder : GeneratorNewProjectWizardBuilderAdapter(MavenArchetypeNewProjectWizard()) {
    override fun getWeight(): Int = JVM_WEIGHT + 100
  }

  private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
    override fun setupAssets(project: Project) {
      outputDirectory = "$path/$name"
      addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
    }
  }
}