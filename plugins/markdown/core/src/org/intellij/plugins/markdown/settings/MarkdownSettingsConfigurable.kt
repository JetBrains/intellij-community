// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import com.intellij.util.application
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.settings.pandoc.PandocSettingsPanel
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel

class MarkdownSettingsConfigurable(private val project: Project): BoundSearchableConfigurable(
  MarkdownBundle.message("markdown.settings.name"),
  MarkdownBundle.message("markdown.settings.name"),
  _id = ID
) {
  private val settings
    get() = MarkdownSettings.getInstance(project)

  private var customStylesheetEditor: Editor? = null

  override fun createPanel(): DialogPanel {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return panel {
        row {
          label(MarkdownBundle.message("markdown.settings.no.providers"))
        }
      }
    }
    return panel {
      if (MarkdownHtmlPanelProvider.getAvailableProviders().size > 1) {
        htmlPanelProvidersRow()
      }
      row(MarkdownBundle.message("markdown.settings.default.layout")) {
        comboBox(
          model = EnumComboBoxModel(TextEditorWithPreview.Layout::class.java),
          renderer = SimpleListCellRenderer.create("") { it?.getName() ?: "" }
        ).bindItem(settings::splitLayout)
      }
      row(MarkdownBundle.message("markdown.settings.preview.layout.label")) {
        comboBox(
          model = DefaultComboBoxModel(arrayOf(false, true)),
          renderer = SimpleListCellRenderer.create("", ::presentSplitLayout)
        ).bindItem(settings::isVerticalSplit)
      }.bottomGap(BottomGap.SMALL)
      row {
        checkBox(MarkdownBundle.message("markdown.settings.preview.auto.scroll.checkbox"))
          .bindSelected(settings::isAutoScrollEnabled)
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.enable.injections"))
          .bindSelected(settings::areInjectionsEnabled)
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.enable.enhance.editing.experience"))
          .bindSelected(settings::isEnhancedEditingEnabled)
      }
      row {
        checkBox(MarkdownBundle.message("markdown.settings.hide.errors"))
          .bindSelected(settings::hideErrorsInCodeBlocks)
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
      customCssRow()
      pandocSettingsRow()
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
        .bindItem(settings::previewPanelProviderInfo)
    }
  }

  private fun validateCustomStylesheetPath(builder: ValidationInfoBuilder, textField: TextFieldWithBrowseButton): ValidationInfo? {
    return builder.run {
      val fieldText = textField.text
      when {
        !FileUtil.exists(fieldText) -> error(MarkdownBundle.message("dialog.message.path.error", fieldText))
        else -> null
      }
    }
  }

  private fun Panel.customCssRow() {
    collapsibleGroup(MarkdownBundle.message("markdown.settings.css.title.name")) {
      row {
        val externalCssCheckBox = checkBox(MarkdownBundle.message("markdown.settings.external.css.path.label"))
          .bindSelected(settings::useCustomStylesheetPath)
          .gap(RightGap.SMALL)
        textFieldWithBrowseButton()
          .applyToComponent {
            text = settings.customStylesheetPath ?: ""
          }
          .horizontalAlign(HorizontalAlign.FILL)
          .enabledIf(externalCssCheckBox.selected)
          .applyIfEnabled()
          .validationOnInput(::validateCustomStylesheetPath)
          .validationOnApply(::validateCustomStylesheetPath)
          .apply {
            onApply { settings.customStylesheetPath = component.text.takeIf { externalCssCheckBox.component.isSelected } }
            onIsModified { externalCssCheckBox.component.isSelected && settings.customStylesheetPath != component.text }
          }
      }
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
          .horizontalAlign(HorizontalAlign.FILL)
          .onApply { settings.customStylesheetText = runReadAction { editor.document.text } }
          .onIsModified { settings.customStylesheetText != runReadAction { editor.document.text.takeIf { it.isNotEmpty() } } }
          .onReset { resetEditorText(settings.customStylesheetText ?: "") }
        customStylesheetEditor = editor
        setEditorReadonlyState(isReadonly = !editorCheckbox.component.isSelected)
      }
    }
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
          .horizontalAlign(HorizontalAlign.FILL)
          .apply {
            onApply { component.apply() }
            onIsModified { component.isModified() }
            onReset { component.reset() }
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

  private fun Panel.extensionsListRow(): ButtonsGroup {
    return buttonsGroup(MarkdownBundle.message("markdown.settings.preview.extensions.name")) {
      val extensions = MarkdownExtensionsUtil.collectConfigurableExtensions()
      for (extension in extensions) {
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

    private fun presentSplitLayout(splitLayout: Boolean?): @Nls String {
      return when (splitLayout) {
        false -> MarkdownBundle.message("markdown.settings.preview.layout.horizontal")
        true -> MarkdownBundle.message("markdown.settings.preview.layout.vertical")
        else -> ""
      }
    }
  }
}
