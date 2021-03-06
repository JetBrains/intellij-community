// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.util.ui.codereview.SingleValueModel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.awaitLoaded
import javax.swing.JComponent

internal class SpaceChatEditableComponent(
  project: Project,
  lifetime: Lifetime,
  content: JComponent,
  message: SpaceChatItem
) : BorderLayoutPanel() {
  val editingModel: SingleValueModel<Boolean> = SingleValueModelImpl(false)

  init {
    isOpaque = false
    val submittableModel = object : SubmittableTextFieldModelBase("") {
      override fun submit() {
        val editingVm = message.editingVm.value
        val newText = document.text
        if (editingVm != null) {
          val id = editingVm.message.id
          launch(lifetime, Ui) {
            val chat = editingVm.channel.awaitLoaded(lifetime)
            if (newText.isBlank()) {
              SpaceStatsCounterCollector.SEND_EDIT_MESSAGE.log(project, true)
              chat?.deleteMessage(id)
            }
            else {
              SpaceStatsCounterCollector.SEND_EDIT_MESSAGE.log(project, false)
              chat?.alterMessage(id, newText)
            }
          }
        }
        message.stopEditing()
      }
    }

    message.editingVm.forEach(lifetime) { editingVm ->
      if (editingVm == null) {
        editingModel.value = false
        return@forEach
      }
      val workspace = SpaceWorkspaceComponent.getInstance().workspace.value ?: return@forEach
      runWriteAction {
        submittableModel.document.setText(workspace.completion.editable(editingVm.message.text))
      }
      editingModel.value = true
      SpaceStatsCounterCollector.START_EDIT_MESSAGE.log(project)
    }
    addToCenter(
      ToggleableContainer.create(editingModel, { content }, {
        SubmittableTextField(SpaceBundle.message("chat.message.edit.action.text"), submittableModel, onCancel = {
          SpaceStatsCounterCollector.DISCARD_EDIT_MESSAGE.log(project)
          message.stopEditing()
        })
      })
    )
  }
}