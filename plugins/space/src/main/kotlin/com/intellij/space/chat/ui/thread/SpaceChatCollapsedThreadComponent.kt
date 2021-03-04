// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.threads.M2ThreadPreviewVm
import circlet.platform.api.toKDateTime
import circlet.platform.client.resolve
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAutoUpdatableComponentService
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createCollapsedThreadComponent(
  project: Project,
  lifetime: Lifetime,
  message: SpaceChatItem,
  threadActionsFactory: SpaceChatThreadActionsFactory?,
  withFirst: Boolean = true
): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(8)
  }
  val threadPreview = message.threadPreview
  val loadingThreadsSqLifetime = SequentialLifetimes(lifetime)
  message.loadingThread.forEach(lifetime) { loadingThread ->
    val loadingThreadLifetime = loadingThreadsSqLifetime.next()
    contentPanel.removeAll()
    if (loadingThread != null) {
      if (!loadingThread.isCompleted) {
        val loadingThreadLabel = JBLabel(SpaceBundle.message("chat.message.loading.thread.label")).apply {
          foreground = UIUtil.getContextHelpForeground()
        }
        contentPanel.addToCenter(loadingThreadLabel)
        contentPanel.revalidate()
        contentPanel.repaint()
      }
      launch(lifetime, Ui) {
        val threadValue = loadingThread.await()
        val threadComponent = createThreadComponent(
          project, lifetime, threadValue,
          pendingStateProvider = { false },
          threadActionsFactory = threadActionsFactory,
          folded = false,
          withFirst = withFirst
        )
        threadValue.mvms.forEach(loadingThreadLifetime) { messages ->
          contentPanel.isVisible = messages.messages.size > if (withFirst) 0 else 1
        }
        contentPanel.removeAll()
        contentPanel.addToCenter(threadComponent)
        contentPanel.revalidate()
        contentPanel.repaint()
      }
    }
    else {
      if (threadPreview != null) {
        val actionsComponent = createActionsComponent(project, lifetime, message, threadPreview, threadActionsFactory)
        contentPanel.addToCenter(actionsComponent)
        threadPreview.messageCount.forEach(loadingThreadLifetime) { count ->
          contentPanel.isVisible = count > 0
        }
      }
      else {
        contentPanel.isVisible = false
      }
      contentPanel.revalidate()
      contentPanel.repaint()
    }
  }
  return contentPanel
}

private fun createActionsComponent(
  project: Project,
  lifetime: Lifetime,
  message: SpaceChatItem,
  threadPreview: M2ThreadPreviewVm,
  threadActionsFactory: SpaceChatThreadActionsFactory?
): JComponent {
  val authorsPanel = createThreadAuthorAvatarsComponent(lifetime, threadPreview)
  val dateComponent = createDateComponent(lifetime, threadPreview.lastReplyTime)
  val repliesComponent = createRepliesLink(project, lifetime, threadPreview.messageCount) {
    launch(lifetime, Ui) {
      message.loadThread(lifetime)
    }
  }
  return JPanel(HorizontalLayout(JBUI.scale(5))).apply {
    isOpaque = false
    add(authorsPanel)
    add(repliesComponent)
    add(dateComponent)
    threadActionsFactory?.createActionsComponent()?.let { add(it) }
  }
}

private fun createThreadAuthorAvatarsComponent(lifetime: Lifetime, threadPreview: M2ThreadPreviewVm): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
  }
  val avatarsProvider = SpaceAvatarProvider(lifetime, contentPanel, SpaceChatAvatarType.THREAD.size)
  threadPreview.authors.forEach(lifetime) { authors ->
    contentPanel.removeAll()
    if (authors.isNullOrEmpty()) {
      contentPanel.border = JBUI.Borders.emptyLeft(10)
    }
    else {
      contentPanel.border = JBUI.Borders.empty()
      val icons = authors.take(3).map { avatarsProvider.getIcon(it.resolve()) }
      val iconsPanel = JPanel(HorizontalLayout(1)).apply {
        isOpaque = false
        for (icon in icons) {
          add(JBLabel(icon))
        }
      }
      contentPanel.addToCenter(iconsPanel)
      contentPanel.revalidate()
      contentPanel.repaint()
    }
  }
  return contentPanel
}

private fun createDateComponent(lifetime: Lifetime, lastReplyTime: Property<Long?>): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
  }
  lastReplyTime.forEach(lifetime) { time ->
    contentPanel.removeAll()
    if (time == null) {
      contentPanel.isVisible = false
    }
    else {
      contentPanel.isVisible = true
      val dateComponent = SpaceAutoUpdatableComponentService.createAutoUpdatableComponent {
        JBLabel(HtmlChunk.text(time.toKDateTime().formatPrettyDateTime()).toString()).apply { // NON-NLS
          putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
          foreground = UIUtil.getContextHelpForeground()
          setCopyable(true)
        }
      }
      contentPanel.addToCenter(dateComponent)
    }
    contentPanel.revalidate()
    contentPanel.repaint()
  }
  return contentPanel
}

private fun createRepliesLink(project: Project, lifetime: Lifetime, messagesCount: Property<Int>, action: () -> Unit): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
  }
  messagesCount.forEach(lifetime) { count ->
    val repliesLink = LinkLabel.create(SpaceBundle.message("chat.message.replies.link", count)) {
      SpaceStatsCounterCollector.OPEN_THREAD.log(project)
      action()
    }
    contentPanel.removeAll()
    contentPanel.addToCenter(repliesLink)
    contentPanel.revalidate()
    contentPanel.repaint()
  }
  return contentPanel
}