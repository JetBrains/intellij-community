// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.layout.selectedValueIs
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionsBaseDialog
import java.io.File
import javax.swing.JList

internal class MarkdownExportDialog(
  targetFile: VirtualFile,
  suggestedFilePath: String,
  project: Project
) : MarkdownFileActionsBaseDialog(project, suggestedFilePath, targetFile) {
  private lateinit var fileTypeSelector: ComboBox<MarkdownExportProvider>
  private lateinit var selectedFileType: MarkdownExportProvider

  private val supportedExportProviders: List<MarkdownExportProvider>
    get() = MarkdownExportProvider.allProviders

  init {
    title = MarkdownBundle.message("markdown.export.from.docx.dialog.title")
    setOKButtonText(MarkdownBundle.message("markdown.export.dialog.ok.button"))
    okAction.isEnabled = selectedFileType.validate(project, targetFile).isNullOrEmpty()
  }

  override fun doAction(selectedFileUrl: String) {
    val provider = supportedExportProviders.find { it == selectedFileType } ?: return
    val outputFile = "$selectedFileUrl.${provider.formatDescription.extension}"

    provider.exportFile(project, file, outputFile)
  }

  override fun getFileNameIfExist(dir: String, fileNameWithoutExtension: String): String? {
    val fullName = "$fileNameWithoutExtension.${fileTypeSelector.item.formatDescription.extension}"
    return if (FileUtil.exists(FileUtil.join(dir, fullName))) fullName else null
  }

  override fun Panel.createFileTypeField() {
    row {
      val fileTypeProperty = PropertyGraph().lazyProperty { selectedFileType }
      fileTypeProperty.afterChange {
        selectedFileType = it
        okAction.isEnabled = it.validate(project, file).isNullOrEmpty()
      }

      selectedFileType = findFirstValidProvider() ?: supportedExportProviders.first()
      label(MarkdownBundle.message("markdown.export.dialog.filetype.label"))
      fileTypeSelector = comboBox(supportedExportProviders, FileTypeRenderer())
        .bindItem(fileTypeProperty)
        .validationOnApply { validateFileType(it) }
        .focused()
        .component
    }
  }

  override fun Panel.createSettingsComponents() {
    supportedExportProviders
      .mapNotNull {
        with(it) {
          val file = VfsUtil.findFileByIoFile(File(suggestedFilePath), false)
          if (file != null) {
            createSettingsComponent(project, file)?.visibleIf(fileTypeSelector.selectedValueIs(it))
          }
        }
      }
  }

  private fun findFirstValidProvider(): MarkdownExportProvider? =
    supportedExportProviders.find { it.validate(project, file) == null }

  private fun ValidationInfoBuilder.validateFileType(combobox: ComboBox<MarkdownExportProvider>): ValidationInfo? {
    val provider = combobox.item
    val errorMessage = provider.validate(project, file)
    return errorMessage?.let(::error)
  }

  private inner class FileTypeRenderer : SimpleListCellRenderer<MarkdownExportProvider>() {
    override fun customize(
      list: JList<out MarkdownExportProvider>,
      value: MarkdownExportProvider?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      if (value == null) {
        return
      }
      text = value.formatDescription.formatName
      val errorMessage = value.validate(project, file)
      if (errorMessage != null) {
        isEnabled = false
        toolTipText = errorMessage
      }
      else {
        isEnabled = true
      }
    }
  }
}
