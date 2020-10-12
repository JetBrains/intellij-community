// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.client.api.Navigator
import circlet.client.api.TD_MemberProfile
import circlet.client.api.fullName
import circlet.m2.ChannelsVm
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import circlet.platform.api.isTemporary
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItemWithThread
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScrollPaneFactory
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
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.reactive.awaitTrue
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal class SpaceChatPanel(
  private val project: Project,
  private val lifetime: Lifetime,
  parent: Disposable,
  private val channelsVm: ChannelsVm,
  private val chatRecord: Ref<M2ChannelRecord>
) : BorderLayoutPanel() {
  private val server = channelsVm.client.server
  private var lastHoveredMessagePanel: HoverableJPanel? = null

  private val loadingPanel = JBLoadingPanel(BorderLayout(), parent).apply {
    startLoading()
    isOpaque = false
  }

  init {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    addToCenter(loadingPanel)
    loadContentAsync()
  }

  private fun loadContentAsync() = launch(lifetime, Ui) {
    val chatVM = loadChatVM()
    val itemsListModel = SpaceChatItemListModel()
    val contentPanel = createContentPanel(itemsListModel, chatVM)
    contentPanel.addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent?) {
        e ?: return
        val component = UIUtil.getDeepestComponentAt(contentPanel, e.x, e.y) ?: return
        val messageComponent = ComponentUtil.getParentOfType(HoverableJPanel::class.java, component)
        if (messageComponent != lastHoveredMessagePanel) {
          lastHoveredMessagePanel?.hoverStateChanged(false)
          lastHoveredMessagePanel = messageComponent
          messageComponent?.hoverStateChanged(true)
        }
      }
    })
    chatVM.mvms.forEach(lifetime) { messagesViewModel ->
      launch(lifetime, Ui) {
        val chatItems = messagesViewModel.messages.map { messageViewModel ->
          async(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
            messageViewModel.convertToChatItemWithThread(lifetime, channelsVm, messageViewModel.getLink(server))
          }
        }.awaitAll()
        itemsListModel.messageListUpdated(chatItems)
        if (loadingPanel.isLoading) {
          stopLoading(contentPanel)
        }
      }
    }
  }

  private suspend fun loadChatVM(): M2ChannelVm = channelsVm.channel(lifetime, chatRecord).also {
    it.awaitFullLoad(lifetime)
  }

  private fun createContentPanel(model: SpaceChatItemListModel, chatVM: M2ChannelVm): JPanel {
    val avatarSize = JBValue.UIInteger("space.chat.avatar.size", 30)
    val avatarProvider = SpaceAvatarProvider(lifetime, this, avatarSize)
    val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
    val timeline = TimelineComponent(model, itemComponentFactory, offset = 0).apply {
      border = JBUI.Borders.empty(16, 0)
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(24, 20)

      val maxWidth = JBUI.scale(600)

      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .flowY(),
                         AC().size(":$maxWidth:$maxWidth").gap("push"))
      add(timeline, CC().growX().minWidth(""))
      add(createNewMessageField(chatVM), CC().growX().minWidth(""))
    }
  }

  private fun stopLoading(contentPanel: JPanel) {
    loadingPanel.add(ScrollPaneFactory.createScrollPane(
      contentPanel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      isOpaque = false
      viewport.isOpaque = false
    })
    loadingPanel.stopLoading()
    loadingPanel.revalidate()
    loadingPanel.repaint()
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