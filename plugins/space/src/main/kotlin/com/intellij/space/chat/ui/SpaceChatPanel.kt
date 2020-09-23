package com.intellij.space.chat.ui

import circlet.client.api.M2ChannelRecord
import circlet.client.api.Navigator
import circlet.m2.ChannelsVm
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import circlet.platform.api.isTemporary
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.SpaceChatItem.Companion.convertToChatItemWithThread
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.async
import libraries.coroutines.extra.launch
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.Ui
import runtime.reactive.awaitTrue
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal fun createSpaceChatPanel(
  project: Project,
  lifetime: Lifetime,
  parent: Disposable,
  channelsVm: ChannelsVm,
  chatRecord: Ref<M2ChannelRecord>
): JPanel {
  val itemsListModel = SpaceChatItemListModel()
  val server = channelsVm.client.server
  val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server)
  val timeline = TimelineComponent(itemsListModel, itemComponentFactory)

  val avatarSize = JBValue.UIInteger("space.chat.avatar.size", 30)
  itemComponentFactory.avatarProvider = SpaceAvatarProvider(lifetime, timeline, avatarSize)

  val contentPanel = JPanel(null).apply {
    isOpaque = false
    border = JBUI.Borders.empty(24, 20)

    val maxWidth = JBUI.scale(600)

    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .flowY(),
                       AC().size(":$maxWidth:$maxWidth").gap("push"))
    add(timeline, CC().growX().minWidth(""))
  }
  val centerPanel = JBLoadingPanel(BorderLayout(), parent).apply {
    startLoading()
    isOpaque = false
    add(ScrollPaneFactory.createScrollPane(
      contentPanel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      isOpaque = false
      viewport.isOpaque = false
    })
  }
  launch(lifetime, Ui) {
    val chatViewModel = channelsVm.channel(lifetime, chatRecord)
    chatViewModel.awaitFullLoad(lifetime)
    val messages = chatViewModel.mvms.prop
    messages.forEach(lifetime) { messagesViewModel ->
      launch(lifetime, Ui) {
        val chatItems = messagesViewModel.messages.map { messageViewModel ->
          async(lifetime, AppExecutorUtil.getAppExecutorService().asCoroutineDispatcher()) {
            messageViewModel.message.convertToChatItemWithThread(lifetime, channelsVm, messageViewModel.getLink(server))
          }
        }.awaitAll()
        itemsListModel.messageListUpdated(chatItems)
        if (centerPanel.isLoading) {
          centerPanel.stopLoading()
        }
      }
    }
  }
  return JBUI.Panels.simplePanel(centerPanel).apply {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }
}

internal suspend fun M2ChannelVm.awaitFullLoad(lifetime: Lifetime) {
  ready.awaitTrue(lifetime)
  val messages = mvms.prop
  while (messages.value.hasPrev) {
    loadPrev()
  }
  while (messages.value.hasNext) {
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