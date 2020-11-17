// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionRecord
import circlet.code.codeReview
import circlet.m2.channel.M2ChannelVm
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.space.chat.ui.createNewMessageField
import com.intellij.space.chat.ui.thread.SpaceChatThreadActionsFactory
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.Property
import javax.swing.JComponent
import javax.swing.JPanel

internal class SpaceChatDiscussionActionsFactory(private val discussion: Property<CodeDiscussionRecord>) : SpaceChatThreadActionsFactory {
  override fun createActionsComponent(chatVm: M2ChannelVm): JComponent {
    val newMessageStateModel = SingleValueModelImpl(false)
    val actionsPanel = {
      val replyAction = LinkLabel<Any>(SpaceBundle.message("chat.reply.action"), null) { _, _ ->
        newMessageStateModel.value = true
      }
      val resolveReopenLabel = createResolveReopenLabel(chatVm)
      JPanel(HorizontalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        add(replyAction)
        add(resolveReopenLabel)
      }
    }

    return ToggleableContainer.create(
      newMessageStateModel,
      mainComponentSupplier = actionsPanel,
      toggleableComponentSupplier = {
        createNewMessageField(chatVm, onCancel = { newMessageStateModel.value = false })
      }
    )
  }

  private fun createResolveReopenLabel(chatVm: M2ChannelVm): JComponent {
    val reviewService = chatVm.client.codeReview
    fun resolve() {
      val currentDiscussion = discussion.value
      launch(chatVm.lifetime, Ui) {
        reviewService.resolveCodeDiscussion(currentDiscussion.id, !currentDiscussion.resolved)
      }
    }

    val label = LinkLabel<Any>("", null) { _, _ ->
      resolve()
    }
    discussion.forEach(chatVm.lifetime) {
      label.text = if (it.resolved) {
        SpaceBundle.message("chat.reopen.action")
      }
      else {
        SpaceBundle.message("chat.resolve.action")
      }
    }

    return label
  }
}