// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
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
  }

  fun show(onOk: ((MarkdownImageData) -> Unit)?) {
    this.onOk = onOk
    show()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(MarkdownBundle.message("markdown.configure.image.dialog.path.label")) {
      textFieldWithBrowseButton(MarkdownBundle.message("markdown.configure.image.dialog.browse.image.title"),
                                project,
                                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      ).align(AlignX.FILL)
        .bindText(::pathFieldText)
        .focused()
    }
    row {
      val widthLabel = label(MarkdownBundle.message("markdown.configure.image.dialog.width.label"))
      val widthField = textField()
        .columns(8)
        .bindText(::widthFieldText)
      val heightLabel = label(MarkdownBundle.message("markdown.configure.image.dialog.height.label"))
        .gap(RightGap.SMALL)
      val heightField = textField()
        .columns(8)
        .bindText(::heightFieldText)
      checkBox(MarkdownBundle.message("markdown.configure.image.dialog.convert.to.html.label"))
        .bindSelected(::shouldConvertToHtml)
        .apply {
          widthLabel.enabledIf(selected)
          widthField.enabledIf(selected)
          heightLabel.enabledIf(selected)
          heightField.enabledIf(selected)
        }
    }.layout(RowLayout.LABEL_ALIGNED)
    collapsibleGroup(MarkdownBundle.message("markdown.configure.image.dialog.screen.reader.text.panel.title")) {
      row(MarkdownBundle.message("markdown.configure.image.dialog.title.label")) {
        textField()
          .align(AlignX.FILL)
          .bindText(::titleFieldText)
      }
      row {
        label(MarkdownBundle.message("markdown.configure.image.dialog.description.label"))
          .align(AlignY.TOP)
        textArea()
          .align(Align.FILL)
          .bindText(::descriptionFieldText)
          .rows(4)
      }.layout(RowLayout.LABEL_ALIGNED)
        .resizableRow()
    }.resizableRow()
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
  }
}
