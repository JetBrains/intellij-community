// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.identifier
import circlet.code.api.CodeReviewRecord
import circlet.code.api.identifier
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.space.vcs.review.editIconButton
import com.intellij.ui.EditorCustomization
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.SoftWrapsEditorCustomization
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xml.util.XmlStringUtil
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import libraries.coroutines.extra.launch
import runtime.Ui
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.KeyStroke

internal class TitleComponent(private val client: KCircletClient,
                              override val lifetime: Lifetime,
                              private val detailsVm: SpaceReviewDetailsVm<CodeReviewRecord>) : Lifetimed {
  private val titleLabel = HtmlEditorPane().apply {
    font = font.deriveFont((font.size * 1.2).toFloat())
  }
  private val editor: EditorTextField = createTitleEditor()

  private fun createTitleEditor(): EditorTextField {
    val features: MutableSet<EditorCustomization> = HashSet()
    features.add(SoftWrapsEditorCustomization.ENABLED)
    val editorField = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.language, detailsVm.ideaProject, features)
    object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        updateTitle()
      }
    }.registerCustomShortcutSet(CustomShortcutSet(ENTER), editorField)
    editorField.font = JBFont.create(editorField.font).biggerOn(3.0f)
    return editorField
  }

  private var editTitleButton: InlineIconButton
  private val saveTitleButton = editIconButton().apply {
    actionListener = ActionListener { updateTitle() }
  }

  private fun updateTitle() {
    launch(lifetime, Ui) {
      client.codeReview.editReviewTitle(detailsVm.projectKey.identifier, detailsVm.review.value.identifier, editor.text)
      toTitleMode()
    }
  }

  val view: BorderLayoutPanel = BorderLayoutPanel().andTransparent()

  init {
    editTitleButton = editIconButton().apply {
      actionListener = ActionListener { toEditMode() }
    }.apply {
      alignmentY = JComponent.TOP_ALIGNMENT
    }

    toTitleMode()

    detailsVm.title.forEach(lifetime) {
      titleLabel.text = XmlStringUtil.wrapInHtml(it)
      editor.text = it
    }
  }

  private fun toEditMode() {
    view.removeAll()
    view.addToCenter(editor)
      .addToRight(BorderLayoutPanel().addToTop(saveTitleButton).andTransparent())
    view.validate()
    view.repaint()
  }

  private fun toTitleMode() {
    view.removeAll()

    view.addToCenter(titleLabel)
      .addToRight(BorderLayoutPanel().addToTop(editTitleButton).andTransparent())
    view.validate()
    view.repaint()
  }
}

private val ENTER: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
private val SHIFT_ENTER: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.VK_SHIFT)

