// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.thread

import circlet.m2.threads.M2ThreadPreviewVm
import circlet.platform.api.toKDateTime
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAutoUpdatableComponentService
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
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
  threadActionsFactory: SpaceChatThreadActionsFactory,
  withFirst: Boolean = true
): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.empty(8, 10, 0, 0)
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
          project, lifetime, threadValue, threadActionsFactory,
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
        val actionsComponent = createActionsComponent(lifetime, message, threadPreview)
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

private fun createActionsComponent(lifetime: Lifetime, message: SpaceChatItem, threadPreview: M2ThreadPreviewVm): JComponent {
  val dateComponent = createDateComponent(lifetime, threadPreview.lastReplyTime)
  val repliesComponent = createRepliesLink(lifetime, threadPreview.messageCount) {
    launch(lifetime, Ui) {
      message.loadThread(lifetime)
    }
  }
  return JPanel(HorizontalLayout(JBUI.scale(5))).apply {
    isOpaque = false
    add(repliesComponent)
    add(dateComponent)
  }
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
        HtmlEditorPane().apply {
          putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
          foreground = UIUtil.getContextHelpForeground()
          setBody(HtmlChunk.text(time.toKDateTime().formatPrettyDateTime()).toString()) // NON-NLS
        }
      }
      contentPanel.addToCenter(dateComponent)
    }
    contentPanel.revalidate()
    contentPanel.repaint()
  }
  return contentPanel
}

private fun createRepliesLink(lifetime: Lifetime, messagesCount: Property<Int>, action: () -> Unit): JComponent {
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
  }
  messagesCount.forEach(lifetime) { count ->
    val repliesLink = LinkLabel.create(SpaceBundle.message("chat.message.replies.link", count), action)
    contentPanel.removeAll()
    contentPanel.addToCenter(repliesLink)
    contentPanel.revalidate()
    contentPanel.repaint()
  }
  return contentPanel
}