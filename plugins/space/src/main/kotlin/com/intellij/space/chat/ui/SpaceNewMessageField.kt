// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.m2.channel.M2ChannelVm
import com.intellij.openapi.application.runWriteAction
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createNewMessageField(
  chatVM: M2ChannelVm,
  avatarType: SpaceChatAvatarType,
  pendingStateProvider: () -> Boolean = { false },
  onCancel: (() -> Unit)? = null
): JComponent {
  val submittableModel = object : SubmittableTextFieldModelBase("") {
    override fun submit() {
      chatVM.sendMessage(document.text, pending = pendingStateProvider())
      runWriteAction {
        document.setText("")
      }
    }
  }

  return JPanel(null).apply {
    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[][]"
    }
    val avatarProvider = SpaceAvatarProvider(chatVM.lifetime, this, avatarType.size)
    val avatar = avatarProvider.getIcon(SpaceWorkspaceComponent.getInstance().workspace.value!!.me.value)
    val avatarComponent = JBUI.Panels.simplePanel(JBLabel(avatar)).apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight(SpaceChatItemComponentFactory.Item.AVATAR_GAP)
    }
    val submittableTextField = SubmittableTextField(
      SpaceBundle.message("chat.comment.action.text"),
      submittableModel,
      onCancel = onCancel
    )
    add(avatarComponent, CC().pushY())
    add(submittableTextField, CC().growX().pushX().alignY("center"))
  }
}