// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2TextItemContent
import circlet.client.api.Navigator
import circlet.client.api.mc.MCMessage
import circlet.code.api.*
import circlet.completion.mentions.MentionConverter
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.resolve
import circlet.platform.client.resolveAll
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.html
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.impl.SpaceChatItemImpl.Companion.convertToChatItem
import com.intellij.space.chat.ui.message.MessageTitleComponent
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RoundedLineBorder
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
import com.intellij.util.ui.UIUtil.CONTRAST_BORDER_COLOR
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.codereview.timeline.thread.TimelineThreadCommentsPanel
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.reactive.awaitLoaded
import java.awt.*
import javax.swing.*

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
  private val avatarProvider: SpaceAvatarProvider
) : TimelineItemComponentFactory<SpaceChatItem> {

  /**
   * Method should return [HoverableJPanel] because it is used to implement hovering properly
   *
   * @see SpaceChatPanel
   */
  override fun createComponent(item: SpaceChatItem): HoverableJPanel {
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
          val user = details.uid.resolve().link(server)
          val text = when (details.changeType) {
            ReviewerChangedType.Joined -> SpaceBundle.message("chat.reviewer.added", user)
            ReviewerChangedType.Left -> SpaceBundle.message("chat.reviewer.removed", user)
          }
          EventMessagePanel(createSimpleMessagePanel(text))
        }
        is MergeRequestMergedEvent -> EventMessagePanel(
          createSimpleMessagePanel(
            HtmlChunk.raw(
              SpaceBundle.message(
                "chat.review.merged",
                HtmlChunk.text(details.sourceBranch).bold(), // NON-NLS
                HtmlChunk.text(details.targetBranch).bold() // NON-NLS
              )
            ).wrapWith(html()).toString()
          )
        )
        is MergeRequestBranchDeletedEvent -> EventMessagePanel(
          createSimpleMessagePanel(
            HtmlChunk.raw(
              SpaceBundle.message("chat.review.deleted.branch", HtmlChunk.text(details.branch).bold()) // NON-NLS
            ).wrapWith(html()).toString()
          )
        )
        is MCMessage -> EventMessagePanel(createSimpleMessagePanel(item.text))
        else -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      item.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarProvider.iconSize.get()),
      MessageTitleComponent(lifetime, item, server),
      createEditableContent(component, item)
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

  private fun createEditableContent(content: JComponent, message: SpaceChatItem): JComponent {
    val submittableModel = object : SubmittableTextFieldModelBase("") {
      override fun submit() {
        val editingVm = message.editingVm.value
        val newText = document.text
        if (editingVm != null) {
          val id = editingVm.message.id
          launch(lifetime, Ui) {
            val chat = editingVm.channel.awaitLoaded(lifetime)
            if (newText.isBlank()) {
              chat?.deleteMessage(id)
            }
            else {
              chat?.alterMessage(id, newText)
            }
          }
        }
        message.stopEditing()
      }
    }

    val editingStateModel = SingleValueModelImpl(false)
    message.editingVm.forEach(lifetime) { editingVm ->
      if (editingVm == null) {
        editingStateModel.value = false
        return@forEach
      }
      val workspace = space.workspace.value ?: return@forEach
      runWriteAction {
        submittableModel.document.setText(workspace.completion.editable(editingVm.message.text))
      }
      editingStateModel.value = true
    }
    return ToggleableContainer.create(editingStateModel, { content }, {
      SubmittableTextField(SpaceBundle.message("chat.message.edit.action.text"), submittableModel, onCancel = { message.stopEditing() })
    })
  }

  private fun JComponent.withThread(lifetime: Lifetime, thread: M2ChannelVm, withFirst: Boolean = true): JComponent {
    return JPanel(VerticalLayout(0)).also { panel ->
      panel.isOpaque = false

      val threadAvatarSize = JBValue.UIInteger("space.chat.thread.avatar.size", 20)
      val avatarProvider = SpaceAvatarProvider(lifetime, panel, threadAvatarSize)

      val itemsListModel = SpaceChatItemListModel()

      // TODO: don't subscribe on thread changes in factory
      thread.mvms.forEach(lifetime) { messageList ->
        itemsListModel.messageListUpdated(
          messageList.messages
            .drop(if (withFirst) 0 else 1)
            .map { it.convertToChatItem(it.getLink(server)) }
        )
      }

      val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server, avatarProvider)
      val threadTimeline = TimelineThreadCommentsPanel(
        itemsListModel,
        commentComponentFactory = itemComponentFactory::createComponent,
        offset = 0
      ).apply {
        border = JBUI.Borders.empty(10, 0)
      }

      val replyComponent = createReplyComponent(thread).apply {
        border = JBUI.Borders.empty()
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
      isOpaque = true
      background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      border = RoundedLineBorder(CONTRAST_BORDER_COLOR, IdeBorderFactory.BORDER_ROUNDNESS)
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
    val nameLabel = JBLabel(name, null, SwingConstants.LEFT).setCopyable(true)

    return NonOpaquePanel(HorizontalLayout(JBUI.scale(5))).apply {
      add(nameLabel)

      if (parentPath.isNotBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
          setCopyable(true)
        })
      }
    }
  }

  private fun createSimpleMessagePanel(message: SpaceChatItem) = createSimpleMessagePanel(message.text)

  private fun createSimpleMessagePanel(@Nls text: String) = HtmlEditorPane().apply {
    setBody(MentionConverter.html(text, server)) // NON-NLS
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

  private class Item(avatar: Icon, private val title: MessageTitleComponent, content: JComponent) : HoverableJPanel() {
    companion object {
      val AVATAR_GAP: Int
        get() = UI.scale(8)
    }

    init {
      layout = BorderLayout()
      val avatarPanel = BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        addToTop(userAvatar(avatar))
      }
      val rightPart = JPanel(VerticalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(AVATAR_GAP)
        add(title, VerticalLayout.FILL_HORIZONTAL)
        add(content, VerticalLayout.FILL_HORIZONTAL)
      }
      isOpaque = false
      border = JBUI.Borders.empty(10)
      add(avatarPanel, BorderLayout.WEST)
      add(rightPart, BorderLayout.CENTER)
    }

    private fun userAvatar(avatar: Icon) = LinkLabel<Any>("", avatar)

    override fun hoverStateChanged(isHovered: Boolean) {
      title.actionsPanel.isVisible = isHovered
      title.revalidate()
      title.repaint()
    }
  }
}