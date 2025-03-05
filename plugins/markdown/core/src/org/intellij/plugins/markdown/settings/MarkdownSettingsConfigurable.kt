// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.application
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.*
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.settings.MarkdownSettingsUtil.belongsToTheProject
import org.intellij.plugins.markdown.settings.pandoc.PandocSettingsPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

internal class MarkdownSettingsConfigurable(private val project: Project): BoundSearchableConfigurable(
  MarkdownBundle.message("markdown.settings.name"),
  MarkdownBundle.message("markdown.settings.name"),
  _id = ID
) {
  private val settings
    get() = MarkdownSettings.getInstance(project)

  private var customStylesheetEditor: Editor? = null

  private fun isPreviewAvailable(): Boolean {
    return MarkdownHtmlPanelProvider.hasAvailableProviders()
  }

  private fun previewDependentOptionsBlock(block: () -> Unit) {
    if (isPreviewAvailable()) {
      block.invoke()
    }
  }

  private fun Panel.showPreviewUnavailableWarningIfNeeded() {
    if (!isPreviewAvailable()) {
      row {
        label(MarkdownBundle.message("markdown.settings.no.providers"))
      }
    }
  }

  override fun createPanel(): DialogPanel {
    return panel {
      useNewComboBoxRenderer()

      showPreviewUnavailableWarningIfNeeded()
      previewDependentOptionsBlock {
        if (MarkdownHtmlPanelProvider.getAvailableProviders().size > 1) {
          htmlPanelProvidersRow()
        }
        row(MarkdownBundle.message("markdown.settings.default.layout")) {
          comboBox(
            model = EnumComboBoxModel(TextEditorWithPreview.Layout::class.java),
            renderer = textListCellRenderer("") { it.getName() }
          ).bindItem(settings::splitLayout.toNullableProperty()).widthGroup(comboBoxWidthGroup)
        }
        row(MarkdownBundle.message("markdown.settings.preview.layout.label")) {
          comboBox(
            model = DefaultComboBoxModel(arrayOf(false, true)),
            renderer = textListCellRenderer("", ::presentSplitLayout)
          ).bindItem(settings::isVerticalSplit.toNullableProperty()).widthGroup(comboBoxWidthGroup)
        }.bottomGap(BottomGap.SMALL)
        row(label = MarkdownBundle.message("markdown.settings.preview.font.size")) {
          previewFontSizeField()
        }
        row {
          checkBox(MarkdownBundle.message("markdown.settings.preview.auto.scroll.checkbox"))
            .bindSelected(settings::isAutoScrollEnabled)
        }
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.enable.injections"))
          .bindSelected(settings::areInjectionsEnabled)
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.show.problems"))
          .bindSelected(settings::showProblemsInCodeBlocks)
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.group.documents.in.project.tree"))
          .bindSelected(settings::isFileGroupingEnabled)
          .onApply { ProjectView.getInstance(project).refresh() }
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.commandrunner.text")).apply {
          bindSelected(
            getter = { CommandRunnerExtension.isExtensionEnabled() },
            setter = { MarkdownExtensionsSettings.getInstance().extensionsEnabledState[CommandRunnerExtension.extensionId] = it }
          )
          onApply { notifyExtensionsChanged() }
        }
      }.bottomGap(BottomGap.SMALL)
      extensionsListRow().apply {
        onApply { notifyExtensionsChanged() }
      }
      previewDependentOptionsBlock {
        customCssRow()
      }
      pandocSettingsRow()
      row {
        configureSmartKeysLinkComment()
      }
    }
  }

  private fun Row.configureSmartKeysLinkComment() {
    comment(comment = MarkdownBundle.message("markdown.settings.smart.keys.comment")) {
      DataManager.getInstance().dataContextFromFocusAsync.onSuccess { context ->
        if (context == null) {
          return@onSuccess
        }
        val settings = context.getData(Settings.KEY) ?: return@onSuccess
        settings.select(settings.find(MarkdownSmartKeysConfigurable.ID))
      }
    }
  }

  private fun notifyExtensionsChanged() {
    val publisher = application.messageBus.syncPublisher(MarkdownExtensionsSettings.ChangeListener.TOPIC)
    publisher.extensionsSettingsChanged(fromSettingsDialog = true)
  }

  private fun Panel.htmlPanelProvidersRow(): Row {
    return row(MarkdownBundle.message("markdown.settings.preview.providers.label")) {
      val providers = MarkdownHtmlPanelProvider.getProviders().map { it.providerInfo }
      comboBox(model = DefaultComboBoxModel(providers.toTypedArray()))
        .bindItem(settings::previewPanelProviderInfo.toNullableProperty())
        .widthGroup(comboBoxWidthGroup)
    }
  }

  private fun Row.previewFontSizeField(): Cell<ComboBox<Int>> {
    return comboBox(fontSizeOptions).bindItem(
      getter = { service<MarkdownPreviewSettings>().state.fontSize },
      setter = { value ->
        service<MarkdownPreviewSettings>().update { settings ->
          if (value != null) {
            settings.state.fontSize = value
          }
        }
      }
    ).applyToComponent {
      isEditable = true
    }
  }

  private fun validateCustomStylesheetPath(builder: ValidationInfoBuilder, textField: TextFieldWithBrowseButton): ValidationInfo? {
    val text = textField.text
    val file = runCatching { Path.of(text) }.getOrNull()
    if (file == null || file.notExists() || file.isDirectory()) {
      return builder.error(MarkdownBundle.message("markdown.settings.stylesheet.path.validation.error"))
    }
    if (!belongsToTheProject(project, file)) {
      return builder.error(MarkdownBundle.message("markdown.settings.stylesheet.path.outside.project.error"))
    }
    return null
  }

  private fun Panel.customCssRow() {
    collapsibleGroup(MarkdownBundle.message("markdown.settings.css.title.name")) {
      externalCssPathRow()
      lateinit var editorCheckbox: Cell<JBCheckBox>
      row {
        editorCheckbox = checkBox(MarkdownBundle.message("markdown.settings.custom.css.text.label"))
          .bindSelected(settings::useCustomStylesheetText)
          .applyToComponent {
            addActionListener { setEditorReadonlyState(isReadonly = !isSelected) }
          }
      }
      row {
        val editor = createCustomStylesheetEditor()
        cell(editor.component)
          .align(AlignX.FILL)
          .onApply { settings.customStylesheetText = runReadAction { editor.document.text } }
          .onIsModified { settings.customStylesheetText != runReadAction { editor.document.text.takeIf { it.isNotEmpty() } } }
          .onReset { resetEditorText(settings.customStylesheetText ?: "") }
        customStylesheetEditor = editor
        setEditorReadonlyState(isReadonly = !editorCheckbox.component.isSelected)
      }
    }
  }

  private fun Panel.externalCssPathRow(): Row {
    return row {
      val isDefaultProject = project.isDefault
      val externalCssCheckBox = checkBox(MarkdownBundle.message("markdown.settings.external.css.path.label"))
        .bindSelected(settings::useCustomStylesheetPath)
        .enabled(!isDefaultProject)
        .gap(RightGap.SMALL)
      customCssTextFieldWithBrowserButton()
        .align(AlignX.FILL)
        .enabled(isDefaultProject)
        .enabledIf(externalCssCheckBox.selected)
        .applyIfEnabled()
        .bindText(
          getter = { settings.customStylesheetPath.orEmpty() },
          setter = { settings.customStylesheetPath = it }
        )
      if (isDefaultProject) {
        rowComment(comment = MarkdownBundle.message("markdown.settings.stylesheet.path.disabled.for.default.project"))
      }
    }
  }

  private fun Row.customCssTextFieldWithBrowserButton(): Cell<TextFieldWithBrowseButton> {
    val field = textFieldWithBrowseButton(
      project = project,
      fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("css")
    )
    field.applyToComponent {
      disposable?.let { Disposer.register(it, this@applyToComponent) }
    }
    return field.validationOnInput(::validateCustomStylesheetPath).validationOnApply(::validateCustomStylesheetPath)
  }

  private fun setEditorReadonlyState(isReadonly: Boolean) {
    customStylesheetEditor?.let {
      it.document.setReadOnly(isReadonly)
      it.contentComponent.isEnabled = !isReadonly
    }
  }

  private fun Panel.pandocSettingsRow() {
    collapsibleGroup(MarkdownBundle.message("markdown.settings.pandoc.name")) {
      row {
        cell(PandocSettingsPanel(project))
          .align(AlignX.FILL)
          .apply {
            onApply { component.apply() }
            onIsModified { component.isModified() }
            onReset { component.reset() }
          }
          .applyToComponent {
            disposable?.let { Disposer.register(it, this@applyToComponent) }
          }
      }
    }
  }

  override fun apply() {
    settings.update {
      super.apply()
    }
  }

  override fun disposeUIResources() {
    customStylesheetEditor?.let(EditorFactory.getInstance()::releaseEditor)
    customStylesheetEditor = null
    super.disposeUIResources()
  }

  private fun Panel.extensionsListRow(): ButtonsGroup? {
    val extensions = MarkdownExtensionsUtil.collectConfigurableExtensions()
    val actualExtensions = when {
      isPreviewAvailable() -> extensions
      else -> extensions.filterNot { it is MarkdownBrowserPreviewExtension.Provider }
    }
    if (actualExtensions.none()) {
      return null
    }
    return buttonsGroup(MarkdownBundle.message("markdown.settings.preview.extensions.name")) {
      for (extension in actualExtensions) {
        createExtensionEntry(extension)
      }
    }
  }

  private fun Panel.createExtensionEntry(extension: MarkdownConfigurableExtension) {
    row {
      val extensionsSettings = MarkdownExtensionsSettings.getInstance()
      val extensionCheckBox = checkBox(text = extension.displayName).bindSelected(
        { extensionsSettings.extensionsEnabledState[extension.id] ?: false },
        { extensionsSettings.extensionsEnabledState[extension.id] = it}
      ).gap(RightGap.SMALL)
      extensionCheckBox.enabled((extension as? MarkdownExtensionWithExternalFiles)?.isAvailable ?: true)
      contextHelp(extension.description).gap(RightGap.SMALL)
      if ((extension as? MarkdownExtensionWithDownloadableFiles)?.isAvailable == false) {
        lateinit var installLink: Cell<ActionLink>
        installLink = link(MarkdownBundle.message("markdown.settings.extension.install.label")) {
          MarkdownSettingsUtil.downloadExtension(
            extension,
            enableAfterDownload = false
          )
          extensionCheckBox.enabled(extension.isAvailable)
          installLink.component.isVisible = !extension.isAvailable
          installLink.component.isEnabled = !extension.isAvailable
        }
      }
    }
  }

  private fun createCustomStylesheetEditor(): EditorEx {
    val editorFactory = EditorFactory.getInstance()
    val editorDocument = editorFactory.createDocument(settings.customStylesheetText ?: "")
    val editor = editorFactory.createEditor(editorDocument) as EditorEx
    fillEditorSettings(editor.settings)
    setEditorHighlighting(editor)
    return editor
  }

  private fun setEditorHighlighting(editor: EditorEx) {
    val cssFileType = FileTypeManager.getInstance().getFileTypeByExtension("css")
    if (cssFileType === UnknownFileType.INSTANCE) {
      return
    }
    val editorHighlighter = HighlighterFactory.createHighlighter(
      cssFileType,
      EditorColorsManager.getInstance().globalScheme,
      null
    )
    editor.highlighter = editorHighlighter
  }

  private fun resetEditorText(cssText: String) {
    customStylesheetEditor?.let { editor ->
      if (!editor.isDisposed) {
        runWriteAction {
          val writable = editor.document.isWritable
          editor.document.setReadOnly(false)
          editor.document.setText(cssText)
          editor.document.setReadOnly(!writable)
        }
      }
    }
  }

  private fun fillEditorSettings(editorSettings: EditorSettings) {
    with(editorSettings) {
      isWhitespacesShown = false
      isLineMarkerAreaShown = false
      isIndentGuidesShown = false
      isLineNumbersShown = true
      isFoldingOutlineShown = false
      additionalColumnsCount = 1
      additionalLinesCount = 3
      isUseSoftWraps = false
    }
  }

  companion object {
    const val ID = "Settings.Markdown"
    private const val comboBoxWidthGroup = "Markdown.ComboBoxWidthGroup"

    private fun presentSplitLayout(splitLayout: Boolean): @Nls String {
      return when (splitLayout) {
        false -> MarkdownBundle.message("markdown.settings.preview.layout.horizontal")
        true -> MarkdownBundle.message("markdown.settings.preview.layout.vertical")
      }
    }

    val fontSizeOptions = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72)
  }
}