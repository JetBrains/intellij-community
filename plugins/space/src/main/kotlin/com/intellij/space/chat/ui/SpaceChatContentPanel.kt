// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.client.api.Navigator
import circlet.client.api.TD_MemberProfile
import circlet.client.api.fullName
import circlet.completion.mentions.MentionConverter
import circlet.m2.ChannelsVm
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import circlet.platform.api.isTemporary
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItemWithThread
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.async
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.reactive.awaitTrue
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel

class SpaceChatContentPanel(
  private val project: Project,
  private val lifetime: Lifetime,
  parent: Disposable,
  private val channelsVm: ChannelsVm,
  private val chatRecord: Ref<M2ChannelRecord>,
) : BorderLayoutPanel() {
  companion object {
    fun getChatAvatarSize() = JBValue.UIInteger("space.chat.avatar.size", 30)
  }

  private val server = channelsVm.client.server
  private val loadingPanel = JBLoadingPanel(BorderLayout(), parent).apply {
    startLoading()
    isOpaque = false
  }

  init {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground

    addToCenter(loadingPanel)

    addHoveringSupport()
    loadContentAsync()
  }

  private fun addHoveringSupport() {
    var lastHoveredMessagePanel: HoverableJPanel? = null
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent?) {
        e ?: return
        val component = UIUtil.getDeepestComponentAt(this@SpaceChatContentPanel, e.x, e.y) ?: return
        val messageComponent = ComponentUtil.getParentOfType(HoverableJPanel::class.java, component)
        if (messageComponent != lastHoveredMessagePanel) {
          lastHoveredMessagePanel?.hoverStateChanged(false)
          lastHoveredMessagePanel = messageComponent
          messageComponent?.hoverStateChanged(true)
        }
      }
    })
  }

  private fun loadContentAsync() = launch(lifetime, Ui) {
    val chatVM = loadChatVM()
    val itemsListModel = SpaceChatItemListModel()
    val contentPanel = createContentPanel(itemsListModel, chatVM)

    chatVM.mvms.forEach(lifetime) { messagesViewModel ->
      launch(lifetime, Ui) {
        val chatItems = messagesViewModel.messages.map { messageViewModel ->
          async(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
            messageViewModel.convertToChatItemWithThread(lifetime, channelsVm, messageViewModel.getLink(server))
          }
        }.awaitAll()
        itemsListModel.messageListUpdated(chatItems)
        if (loadingPanel.isLoading) {
          loadingPanel.add(contentPanel, BorderLayout.CENTER)
          loadingPanel.stopLoading()
          loadingPanel.revalidate()
          loadingPanel.repaint()
        }
      }
    }
  }

  private suspend fun loadChatVM(): M2ChannelVm = channelsVm.channel(lifetime, chatRecord).also {
    it.awaitFullLoad(lifetime)
  }

  private fun createContentPanel(model: SpaceChatItemListModel, chatVM: M2ChannelVm): JPanel {
    val avatarSize = getChatAvatarSize()
    val avatarProvider = SpaceAvatarProvider(lifetime, this, avatarSize)
    val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
    val timeline = TimelineComponent(model, itemComponentFactory, offset = 0).apply {
      border = JBUI.Borders.emptyBottom(10)
    }

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(timeline, VerticalLayout.FILL_HORIZONTAL)
      add(createNewMessageField(chatVM), VerticalLayout.FILL_HORIZONTAL)
    }
  }
}

internal suspend fun M2ChannelVm.awaitFullLoad(lifetime: Lifetime) {
  ready.awaitTrue(lifetime)
  val messages = mvms.prop
  while (messages.value.hasPrev) {
    // TODO: remove this temporary fix when it will be fixed in platform
    delay(1)
    loadPrev()
  }
  while (messages.value.hasNext) {
    delay(1)
    loadNext()
  }
}

internal fun M2MessageVm.getLink(hostUrl: String): String? =
  if (canCopyLink && !message.isTemporary()) {
    Navigator.im.message(channelVm.key.value, message).absoluteHref(hostUrl)
  }
  else {
    null
  }

@Nls
internal fun TD_MemberProfile.link(server: String): HtmlChunk =
  HtmlChunk.link(Navigator.m.member(username).absoluteHref(server), name.fullName()) // NON-NLS

@NlsSafe
internal fun processItemText(server: String, @NlsSafe text: String) = MentionConverter.html(text, server)