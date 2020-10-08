// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.m2.channel.M2ChannelVm
import com.intellij.openapi.application.runWriteAction
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import javax.swing.JComponent

internal fun createReplyComponent(chatVM: M2ChannelVm): JComponent {
  val newMessageStateModel = SingleValueModelImpl(false)
  val replyActionSupplier: () -> JComponent = {
    LinkLabel<Any>(SpaceBundle.message("chat.reply.action"), null) { _, _ ->
      newMessageStateModel.value = true
    }
  }
  return ToggleableContainer.create(
    newMessageStateModel,
    mainComponentSupplier = replyActionSupplier,
    toggleableComponentSupplier = {
      createNewMessageField(chatVM, onCancel = { newMessageStateModel.value = false })
    }
  )
}

internal fun createNewMessageField(
  chatVM: M2ChannelVm,
  onCancel: (() -> Unit)? = null
): JComponent {
  val submittableModel = object : SubmittableTextFieldModelBase("") {
    override fun submit() {
      chatVM.sendMessage(document.text)
      runWriteAction {
        document.setText("")
      }
    }
  }

  return SubmittableTextField(SpaceBundle.message("chat.comment.action.text"), submittableModel, onCancel = onCancel)
}