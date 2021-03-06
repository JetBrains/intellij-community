// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.m2.channel.M2ChannelVm
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModel
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createNewMessageField(
  chatVM: M2ChannelVm,
  avatarType: SpaceChatAvatarType,
  statsPlace: SpaceStatsCounterCollector.SendMessagePlace,
  pendingStateProvider: () -> Boolean = { false },
  onCancel: (() -> Unit)? = null,
  onSend: (() -> Unit)? = null
): JComponent {
  val submittableModel = object : SubmittableTextFieldModelBase("") {
    override fun submit() {
      val isPending = pendingStateProvider()
      SpaceStatsCounterCollector.SEND_MESSAGE.log(statsPlace, isPending)
      chatVM.sendMessage(document.text, pending = isPending)
      runWriteAction {
        document.setText("")
      }
      onSend?.invoke()
    }
  }
  return SpaceChatNewMessageWithAvatarComponent(chatVM.lifetime, avatarType, submittableModel, statsPlace, onCancel)
}

internal class SpaceChatNewMessageWithAvatarComponent(
  avatarProviderLifetime: Lifetime,
  avatarType: SpaceChatAvatarType,
  submittableModel: SubmittableTextFieldModel,
  statsPlace: SpaceStatsCounterCollector.SendMessagePlace,
  onCancel: (() -> Unit)?
) : JPanel(null) {
  init {
    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[][]"
    }
    val avatarProvider = SpaceAvatarProvider(avatarProviderLifetime, this, avatarType.size)
    val avatar = avatarProvider.getIcon(SpaceWorkspaceComponent.getInstance().workspace.value!!.me.value)
    val avatarComponent = JBUI.Panels.simplePanel(JBLabel(avatar)).apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight(SpaceChatItemComponentFactory.Item.AVATAR_GAP)
    }
    val submittableTextField = SubmittableTextField(
      SpaceBundle.message("chat.comment.action.text"),
      submittableModel,
      onCancel = {
        if (
          submittableModel.document.text.isBlank() ||
          MessageDialogBuilder.yesNo(
            SpaceBundle.message("chat.message.new.discard.changes.title"),
            SpaceBundle.message("chat.message.new.discard.changes.text")
          ).ask(this)
        ) {
          SpaceStatsCounterCollector.DISCARD_SEND_MESSAGE.log(statsPlace)
          onCancel?.invoke()
        }
      }
    )
    add(avatarComponent, CC().pushY())
    add(submittableTextField, CC().growX().pushX().alignY("center"))
  }
}