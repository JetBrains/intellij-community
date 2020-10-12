// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.*
import circlet.code.api.*
import circlet.completion.mentions.MentionConverter
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.format
import circlet.platform.client.resolve
import circlet.platform.client.resolveAll
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import icons.VcsCodeReviewIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.date.DateFormat
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
  private val avatarProvider: SpaceAvatarProvider
) : TimelineItemComponentFactory<SpaceChatItem> {
  override fun createComponent(item: SpaceChatItem): JComponent {
    val component =
      when (val details = item.details) {
        is CodeDiscussionAddedFeedEvent -> {
          val discussion = details.codeDiscussion.resolve()
          val thread = item.thread!!
          createDiff(project, discussion, thread)
            ?.withThread(lifetime, thread, withFirst = false) ?: createUnsupportedMessageTypePanel(item.link)
        }
        is M2TextItemContent -> {
          val messagePanel = createSimpleMessagePanel(item)
          val thread = item.thread
          if (thread == null) {
            messagePanel
          }
          else {
            messagePanel.withThread(lifetime, thread)
          }
        }
        is ReviewRevisionsChangedEvent -> {
          val review = details.review?.resolve()
          if (review == null) {
            EventMessagePanel(createSimpleMessagePanel(item))
          }
          else {
            details.createComponent(review)
          }
        }
        is ReviewCompletionStateChangedEvent -> EventMessagePanel(createSimpleMessagePanel(item))
        is ReviewerChangedEvent -> {
          val user = details.uid.resolve().link()
          val text = when (details.changeType) {
            ReviewerChangedType.Joined -> SpaceBundle.message("chat.reviewer.added", user)
            ReviewerChangedType.Left -> SpaceBundle.message("chat.reviewer.removed", user)
          }
          EventMessagePanel(createSimpleMessagePanel(text))
        }
        else -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      item.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarProvider.iconSize.get()),
      titleSupplier = { parent -> createMessageTitle(parent, item) },
      component
    )
  }

  private fun ReviewRevisionsChangedEvent.createComponent(review: CodeReviewRecord): JComponent {
    val commitsCount = commits.size
    @Nls val text = when (changeType) {
      ReviewRevisionsChangedType.Created -> SpaceBundle.message("chat.code.review.created.message", commitsCount)
      ReviewRevisionsChangedType.Added -> SpaceBundle.message("chat.commits.added.message", commitsCount)
      ReviewRevisionsChangedType.Removed -> SpaceBundle.message("chat.commits.removed.message", commitsCount)
    }

    val message = createSimpleMessagePanel(text)

    val content = JPanel(VerticalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      add(message)
      commits.resolveAll().forEach { commit ->
        val location = when (changeType) {
          ReviewRevisionsChangedType.Removed -> {
            Navigator.p.project(review.project).revision(commit.repositoryName, commit.revision)
          }
          else -> {
            Navigator.p.project(review.project)
              .reviewFiles(review.number, revisions = listOf(commit.revision))
          }
        }.absoluteHref(server)
        add(LinkLabel<Any?>(
          commit.message?.let { getCommitMessageSubject(it) } ?: SpaceBundle.message("chat.untitled.commit.label"), // NON-NLS
          AllIcons.Vcs.CommitNode,
          LinkListener { _, _ ->
            BrowserUtil.browse(location)
          }
        ))
      }
    }

    return EventMessagePanel(content)
  }

  private fun createUnsupportedMessageTypePanel(messageLink: String?): JComponent {
    val description = if (messageLink != null) {
      SpaceBundle.message("chat.unsupported.message.type.with.link", messageLink)
    }
    else {
      SpaceBundle.message("chat.unsupported.message.type")
    }
    return JBLabel(description, AllIcons.General.Warning, SwingConstants.LEFT).setCopyable(true)
  }

  private fun createDeleteButton(message: SpaceChatItem): JComponent? {
    if (!message.canDelete) {
      return null
    }
    return InlineIconButton(VcsCodeReviewIcons.Delete, VcsCodeReviewIcons.DeleteHovered).apply {
      actionListener = ActionListener {
        launch(lifetime, Ui) {
          message.delete()
        }
      }
    }
  }

  private fun createMessageTitle(parent: JComponent, message: SpaceChatItem): JComponent {
    val authorPanel = HtmlEditorPane().apply {
      setBody(createMessageAuthorChunk(message.author).bold().toString())
    }
    val timePanel = HtmlEditorPane().apply {
      foreground = UIUtil.getContextHelpForeground()
      setBody(HtmlChunk.text(message.created.format(DateFormat.HOURS_AND_MINUTES)).toString()) // NON-NLS
    }
    val actionsPanel = JPanel(HorizontalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      createDeleteButton(message)?.let { add(it) }
    }
    makeVisibleOnHover(actionsPanel, parent)
    return JPanel(HorizontalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      add(authorPanel)
      add(timePanel)
      add(actionsPanel)
      launch(lifetime, Ui) {
        delay(2000)
        if (!message.delivered) {
          add(JBLabel(AnimatedIcon.Default.INSTANCE))
          revalidate()
          repaint()
        }
      }
    }
  }

  private fun createMessageAuthorChunk(author: CPrincipal): HtmlChunk =
    when (val details = author.details) {
      is CUserPrincipalDetails -> {
        val user = details.user.resolve()
        user.link()
      }
      is CExternalServicePrincipalDetails -> {
        val service = details.service.resolve()
        val location = Navigator.manage.oauthServices.service(service)
        HtmlChunk.link(location.href, service.name) // NON-NLS
      }
      else -> {
        HtmlChunk.text(author.name) // NON-NLS
      }
    }

  @Nls
  private fun TD_MemberProfile.link(): HtmlChunk =
    HtmlChunk.link(Navigator.m.member(username).absoluteHref(server), name.fullName()) // NON-NLS

  private fun JComponent.withThread(lifetime: Lifetime, thread: M2ChannelVm, withFirst: Boolean = true): JComponent {
    return JPanel(VerticalLayout(JBUI.scale(10))).also { panel ->
      panel.isOpaque = false

      val threadAvatarSize = JBValue.UIInteger("space.chat.thread.avatar.size", 20)
      val avatarProvider = SpaceAvatarProvider(lifetime, panel, threadAvatarSize)

      val itemsListModel = SpaceChatItemListModel()
      val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
      val threadTimeline = TimelineComponent(itemsListModel, itemComponentFactory)

      val replyComponent = createReplyComponent(thread).apply {
        border = JBUI.Borders.empty()
      }

      // TODO: don't subscribe on thread changes in factory
      thread.mvms.forEach(lifetime) { messageList ->
        launch(lifetime, Ui) {
          itemsListModel.messageListUpdated(
            messageList.messages
              .drop(if (withFirst) 0 else 1)
              .map { it.convertToChatItem(it.getLink(server)) }
          )
        }
      }
      panel.add(this, VerticalLayout.FILL_HORIZONTAL)
      panel.add(threadTimeline, VerticalLayout.FILL_HORIZONTAL)
      panel.add(replyComponent, VerticalLayout.FILL_HORIZONTAL)
    }
  }

  private fun createDiff(
    project: Project,
    discussion: CodeDiscussionRecord,
    thread: M2ChannelVm
  ): JComponent? {
    val fileNameComponent = JBUI.Panels.simplePanel(createFileNameComponent(discussion.anchor.filename!!)).apply {
      isOpaque = false
      border = JBUI.Borders.empty(8)
    }

    val diffEditorComponent = when (val snippet = discussion.snippet!!) {
      is CodeDiscussionSnippet.PlainSnippet -> {
        return null
      }
      is CodeDiscussionSnippet.InlineDiffSnippet -> createDiffComponent(project, discussion.anchor, snippet)
    }.apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }

    val snapshotComponent = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      border = IdeBorderFactory.createRoundedBorder()
      add(fileNameComponent)
      add(diffEditorComponent, VerticalLayout.FILL_HORIZONTAL)
    }
    val panel = JPanel(VerticalLayout(UI.scale(4))).apply {
      isOpaque = false
      add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
    }
    // TODO: don't subscribe on comment in factory
    thread.mvms.forEach(lifetime) {
      val comment = it.messages.first()
      if (panel.componentCount == 2) {
        panel.remove(1)
      }
      panel.add(createSimpleMessagePanel(comment.convertToChatItem(comment.getLink(server))))
    }
    return panel
  }

  private fun createFileNameComponent(filePath: String): JComponent {
    val name = PathUtil.getFileName(filePath)
    val parentPath = PathUtil.getParentPath(filePath)
    val nameLabel = JLabel(name, null, SwingConstants.LEFT)

    return NonOpaquePanel(HorizontalLayout(JBUI.scale(5))).apply {
      add(nameLabel)

      if (parentPath.isNotBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
        })
      }
    }
  }

  private fun createSimpleMessagePanel(message: SpaceChatItem) = createSimpleMessagePanel(message.text)

  private fun createSimpleMessagePanel(@Nls text: String) = HtmlEditorPane().apply {
    setBody(MentionConverter.html(text, server)) // NON-NLS
  }

  private fun JComponent.addOnHoverListener(listener: (isInside: Boolean) -> Unit) {
    var currentState = false
    fun updateCurrentState(newState: Boolean) {
      if (currentState != newState) {
        currentState = newState
        listener(newState)
      }
    }

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        updateCurrentState(true)
      }

      override fun mouseExited(e: MouseEvent?) {
        // TODO: such solution doesn't work for case like parent-> child -> exit
        if (e != null && !this@addOnHoverListener.contains(e.point)) {
          updateCurrentState(false)
        }
      }
    })
  }

  private fun makeVisibleOnHover(component: JComponent, parent: JComponent) {
    component.isVisible = false
    parent.addOnHoverListener { isInside ->
      component.isVisible = isInside
      parent.revalidate()
      parent.repaint()
    }
  }

  private class EventMessagePanel(content: JComponent) : BorderLayoutPanel() {
    private val lineColor = Color(22, 125, 255, 51)

    private val lineWidth
      get() = JBUI.scale(6)
    private val lineCenterX
      get() = lineWidth / 2
    private val yRoundOffset
      get() = lineWidth / 2

    init {
      addToCenter(content)
      isOpaque = false
      border = JBUI.Borders.empty(yRoundOffset, 15, yRoundOffset, 0)
    }

    override fun paint(g: Graphics?) {
      super.paint(g)

      with(g as Graphics2D) {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        color = lineColor
        stroke = BasicStroke(lineWidth.toFloat() / 2 + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)

        drawLine(lineCenterX, yRoundOffset, lineCenterX, height - yRoundOffset)
      }
    }
  }

  private class Item(avatar: Icon, titleSupplier: (parent: JComponent) -> JComponent, content: JComponent) : BorderLayoutPanel() {
    companion object {
      val AVATAR_GAP: Int
        get() = UI.scale(8)
    }

    init {
      val avatarPanel = BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        addToTop(userAvatar(avatar))
      }
      val rightPart = JPanel(VerticalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(AVATAR_GAP)
        add(titleSupplier(this@Item), VerticalLayout.FILL_HORIZONTAL)
        add(content, VerticalLayout.FILL_HORIZONTAL)
      }

      isOpaque = false
      addToLeft(avatarPanel)
      addToCenter(rightPart)
    }

    private fun userAvatar(avatar: Icon) = LinkLabel<Any>("", avatar)
  }
}