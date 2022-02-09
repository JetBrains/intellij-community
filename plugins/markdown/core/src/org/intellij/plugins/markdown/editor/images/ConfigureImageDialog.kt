// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

@ApiStatus.Internal
class ConfigureImageDialog(
  private val project: Project?,
  @NlsContexts.DialogTitle title: String,
  path: String? = null,
  width: String? = null,
  height: String? = null,
  linkTitle: String? = null,
  linkDescriptionText: String? = null,
  private var shouldConvertToHtml: Boolean = false
) : DialogWrapper(project, true) {
  private var pathFieldText = path ?: ""
  private var widthFieldText = width ?: ""
  private var heightFieldText = height ?: ""
  private var titleFieldText = linkTitle ?: ""
  private var descriptionFieldText = linkDescriptionText ?: ""

  private var onOk: ((MarkdownImageData) -> Unit)? = null

  init {
    super.init()
    this.title = title
    isResizable = false
  }

  fun show(onOk: ((MarkdownImageData) -> Unit)?) {
    this.onOk = onOk
    show()
  }

  override fun createCenterPanel(): JComponent = panel {
    fullRow {
      label(MarkdownBundle.message("markdown.configure.image.dialog.path.label")).sizeGroup(ALWAYS_VISIBLE_FIRST_COLUMN_LABELS_SIZE_GROUP)
      textFieldWithBrowseButton(
        ::pathFieldText,
        MarkdownBundle.message("markdown.configure.image.dialog.browse.image.title"),
        project,
        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      ).focused()
    }
    fullRow {
      val widthLabel = label(MarkdownBundle.message("markdown.configure.image.dialog.width.label"))
        .sizeGroup(ALWAYS_VISIBLE_FIRST_COLUMN_LABELS_SIZE_GROUP)
      val widthField = textField(::widthFieldText, 8)
      val heightLabel = label(MarkdownBundle.message("markdown.configure.image.dialog.height.label"))
      val heightField = textField(::heightFieldText, 8)
      checkBox(
        MarkdownBundle.message("markdown.configure.image.dialog.convert.to.html.label"),
        ::shouldConvertToHtml
      ).withLargeLeftGap().apply {
        widthLabel.enableIf(selected)
        widthField.enableIf(selected)
        heightLabel.enableIf(selected)
        heightField.enableIf(selected)
      }
    }
    hideableRow(MarkdownBundle.message("markdown.configure.image.dialog.screen.reader.text.panel.title")) {
      fullRow {
        label(MarkdownBundle.message("markdown.configure.image.dialog.title.label")).sizeGroup(HIDEABLE_FIRST_COLUMN_LABELS_SIZE_GROUP)
        textField(::titleFieldText)
      }
      fullRow {
        label(MarkdownBundle.message("markdown.configure.image.dialog.description.label"))
          .sizeGroup(HIDEABLE_FIRST_COLUMN_LABELS_SIZE_GROUP)
        textArea(::descriptionFieldText, rows = 4)
      }
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    onOk?.invoke(MarkdownImageData(
      path = processInput(pathFieldText),
      width = processInput(widthFieldText),
      height = processInput(heightFieldText),
      title = processInput(titleFieldText),
      description = processInput(descriptionFieldText),
      shouldConvertToHtml = shouldConvertToHtml
    ))
  }

  private fun processInput(text: String): String {
    return text.trim()
  }

  override fun getDimensionServiceKey() = DIMENSION_KEY

  companion object {
    private const val DIMENSION_KEY: @NonNls String = "Markdown.Configure.Image.Dialog.DimensionServiceKey"

    private const val ALWAYS_VISIBLE_FIRST_COLUMN_LABELS_SIZE_GROUP = "ALWAYS_VISIBLE_FIRST_COLUMN_LABELS_SIZE_GROUP"
    private const val HIDEABLE_FIRST_COLUMN_LABELS_SIZE_GROUP = "HIDEABLE_FIRST_COLUMN_LABELS_SIZE_GROUP"
  }
}
