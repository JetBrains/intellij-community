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
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownConfigurableExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
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

  private lateinit var customStylesheetEditor: Editor

  override fun createPanel(): DialogPanel {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return panel {
        fullRow {
          label(MarkdownBundle.message("markdown.settings.no.providers"))
        }
      }
    }
    return panel {
      if (MarkdownHtmlPanelProvider.getAvailableProviders().size > 1) {
        htmlPanelProvidersRow()
      }
      fullRow {
        label(MarkdownBundle.message("markdown.settings.default.layout"))
        comboBox(
          model = EnumComboBoxModel(TextEditorWithPreview.Layout::class.java),
          prop = settings::splitLayout,
          renderer = SimpleListCellRenderer.create("") { it?.getName() ?: "" }
        )
      }
      fullRow {
        label(MarkdownBundle.message("markdown.settings.preview.layout.label"))
        comboBox(
          model = DefaultComboBoxModel(arrayOf(false, true)),
          prop = settings::isVerticalSplit,
          renderer = SimpleListCellRenderer.create("", ::presentSplitLayout)
        )
      }.largeGapAfter()
      fullRow {
        checkBox(MarkdownBundle.message("markdown.settings.preview.auto.scroll.checkbox"), settings::isAutoScrollEnabled)
      }
      fullRow {
        checkBox(MarkdownBundle.message("markdown.settings.enable.injections"), settings::areInjectionsEnabled)
      }
      fullRow {
        checkBox(MarkdownBundle.message("markdown.settings.enable.enhance.editing.experience"), settings::isEnhancedEditingEnabled)
      }
      fullRow {
        checkBox(MarkdownBundle.message("markdown.settings.hide.errors"), settings::hideErrorsInCodeBlocks)
      }.largeGapAfter()
      fullRow {
        label(MarkdownBundle.message("markdown.settings.preview.extensions.name"))
      }
      extensionsListRow().largeGapAfter()
      customCssRow()
      pandocSettingsRow()
    }
  }

  private fun RowBuilder.htmlPanelProvidersRow(): Row {
    return row {
      cell {
        label(MarkdownBundle.message("markdown.settings.preview.providers.label"))
      }
      cell {
        val providers = MarkdownHtmlPanelProvider.getProviders().map { it.providerInfo }
        comboBox(model = DefaultComboBoxModel(providers.toTypedArray()), settings::previewPanelProviderInfo)
      }
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

  private fun RowBuilder.customCssRow(): Row {
    customStylesheetEditor = createCustomStylesheetEditor()
    return hideableRow(MarkdownBundle.message("markdown.settings.css.title.name")) {
      fullRow {
        val externalCssCheckBox = checkBox(MarkdownBundle.message("markdown.settings.external.css.path.label"), prop = settings::useCustomStylesheetPath)
        textFieldWithBrowseButton(value = settings.customStylesheetPath).apply {
          constraints(CCFlags.growX)
          enableIf(externalCssCheckBox.selected)
          applyIfEnabled()
          onApply { settings.customStylesheetPath = component.text.takeIf { externalCssCheckBox.component.isSelected } }
          onIsModified { externalCssCheckBox.component.isSelected && settings.customStylesheetPath != component.text }
          withValidationOnInput(::validateCustomStylesheetPath)
          withValidationOnApply(::validateCustomStylesheetPath)
        }
      }
      lateinit var editorCheckbox: CellBuilder<JBCheckBox>
      fullRow {
        editorCheckbox = checkBox(MarkdownBundle.message("markdown.settings.custom.css.text.label"), prop = settings::useCustomStylesheetText).apply {
          applyToComponent {
            addActionListener { setEditorReadonlyState(isReadonly = !isSelected) }
          }
        }
      }
      fullRow {
        customStylesheetEditor.component(CCFlags.growX).apply {
          onApply { settings.customStylesheetText = runReadAction { customStylesheetEditor.document.text } }
          onIsModified { settings.customStylesheetText != runReadAction { customStylesheetEditor.document.text.takeIf { it.isNotEmpty() } } }
          onReset { resetEditorText(settings.customStylesheetText ?: "") }
        }
        setEditorReadonlyState(isReadonly = !editorCheckbox.component.isSelected)
      }
    }
  }

  private fun setEditorReadonlyState(isReadonly: Boolean) {
    customStylesheetEditor.document.setReadOnly(isReadonly)
    customStylesheetEditor.contentComponent.isEnabled = !isReadonly
  }

  private fun RowBuilder.pandocSettingsRow(): Row {
    return hideableRow(MarkdownBundle.message("markdown.settings.pandoc.name")) {
      fullRow {
        component(PandocSettingsPanel(project)).apply {
          constraints(CCFlags.growX)
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
    EditorFactory.getInstance().releaseEditor(customStylesheetEditor)
    super.disposeUIResources()
  }

  private fun RowBuilder.extensionsListRow(): Row {
    return fullRow {
      row {
        val extensions = MarkdownExtension.all.filterIsInstance<MarkdownConfigurableExtension>()
        for (extension in extensions) {
          createExtensionEntry(extension)
        }
      }
    }
  }

  private fun RowBuilder.createExtensionEntry(extension: MarkdownConfigurableExtension): Row {
    return fullRow {
      val extensionCheckBox = checkBox(
        text = extension.displayName,
        getter = { settings.extensionsEnabledState[extension.id] ?: false },
        setter = { settings.extensionsEnabledState[extension.id] = it}
      )
      extensionCheckBox.enabled((extension as? MarkdownExtensionWithExternalFiles)?.isAvailable == true)
      component(ContextHelpLabel.create(extension.description))
      if ((extension as? MarkdownExtensionWithExternalFiles)?.isAvailable == false) {
        component(ActionLink(MarkdownBundle.message("markdown.settings.extension.install.label"))).apply {
          component.addActionListener {
            MarkdownSettingsUtil.downloadExtension(
              extension,
              enableAfterDownload = false
            )
            extensionCheckBox.enabled(extension.isAvailable)
            component.isVisible = !extension.isAvailable
            component.isEnabled = !extension.isAvailable
          }
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
    if (!customStylesheetEditor.isDisposed) {
      runWriteAction {
        val writable = customStylesheetEditor.document.isWritable
        customStylesheetEditor.document.setReadOnly(false)
        customStylesheetEditor.document.setText(cssText)
        customStylesheetEditor.document.setReadOnly(!writable)
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
    const val ID = "Settings.Markdown.Project"

    private fun presentSplitLayout(splitLayout: Boolean?): @Nls String {
      return when (splitLayout) {
        false -> MarkdownBundle.message("markdown.settings.preview.layout.horizontal")
        true -> MarkdownBundle.message("markdown.settings.preview.layout.vertical")
        else -> ""
      }
    }
  }
}
