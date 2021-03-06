// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.code.api.ReviewerChangedType
import circlet.platform.client.resolve
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.html
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.model.api.SpaceChatItemType.*
import com.intellij.space.chat.ui.discussion.SpaceChatCodeDiscussionComponentFactory
import com.intellij.space.chat.ui.message.MessageTitleComponent
import com.intellij.space.chat.ui.message.SpaceChatMessagePendingHeader
import com.intellij.space.chat.ui.message.SpaceMCMessageComponent
import com.intellij.space.chat.ui.message.SpaceStyledMessageComponent
import com.intellij.space.chat.ui.thread.createCollapsedThreadComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import icons.VcsCodeReviewIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import runtime.Ui
import javax.swing.*

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
  private val avatarProvider: SpaceAvatarProvider
) : TimelineItemComponentFactory<SpaceChatItem> {

  private val codeDiscussionComponentFactory = SpaceChatCodeDiscussionComponentFactory(project, lifetime, server)

  /**
   * Method should return [HoverableJPanel] because it is used to implement hovering properly
   *
   * @see SpaceChatPanel
   */
  override fun createComponent(item: SpaceChatItem): HoverableJPanel {
    val component =
      when (val type = item.type) {
        is CodeDiscussion ->
          codeDiscussionComponentFactory.createComponent(item, type.discussion) ?: createUnsupportedMessageTypePanel(item.link)
        is SimpleText -> SpaceChatEditableComponent(project, lifetime, createSimpleMessageComponent(item), item)
        is ReviewCompletionStateChanged -> SpaceStyledMessageComponent(createSimpleMessageComponent(item))
        is ReviewerChanged -> {
          val user = type.uid.resolve().link()
          val text = when (type.changeType) {
            ReviewerChangedType.Joined -> SpaceBundle.message("chat.reviewer.added", user)
            ReviewerChangedType.Left -> SpaceBundle.message("chat.reviewer.removed", user)
          }
          SpaceStyledMessageComponent(SpaceChatMarkdownTextComponent(server, text))
        }
        is MergeRequestMerged -> SpaceStyledMessageComponent(
          SpaceChatMarkdownTextComponent(
            server,
            HtmlChunk.raw(
              SpaceBundle.message(
                "chat.review.merged",
                HtmlChunk.text(type.sourceBranch).bold(), // NON-NLS
                HtmlChunk.text(type.targetBranch).bold() // NON-NLS
              )
            ).wrapWith(html()).toString()
          )
        )
        is MergeRequestBranchDeleted -> SpaceStyledMessageComponent(
          SpaceChatMarkdownTextComponent(
            server,
            HtmlChunk.raw(
              SpaceBundle.message("chat.review.deleted.branch", HtmlChunk.text(type.branch).bold()) // NON-NLS
            ).wrapWith(html()).toString()
          )
        )
        is ReviewTitleChanged -> SpaceStyledMessageComponent(
          SpaceChatMarkdownTextComponent(
            server,
            HtmlChunk.raw(
              SpaceBundle.message(
                "chat.review.title.changed",
                HtmlChunk.text(type.oldTitle).bold(), // NON-NLS
                HtmlChunk.text(type.newTitle).bold()  // NON-NLS
              )
            ).wrapWith(html()).toString()
          )
        )
        is MCMessage -> SpaceMCMessageComponent(server, type.content, item.attachments)
        is Deleted -> SpaceChatMarkdownTextComponent(server, getGrayTextHtml(SpaceBundle.message("chat.message.removed.text")))
        is Unknown -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      createAvatarIcon(item),
      createTitleComponent(item),
      SpaceChatMessagePendingHeader(item),
      component.addThreadComponentIfNeeded(item).addStartThreadField(item)
    )
  }

  private fun createTitleComponent(item: SpaceChatItem): MessageTitleComponent? {
    if (item.type == Deleted) {
      return null
    }
    return MessageTitleComponent(project, lifetime, item)
  }

  private fun createAvatarIcon(item: SpaceChatItem): Icon {
    val user = item.author.asUser
    if (user != null) {
      return avatarProvider.getIcon(user)
    }
    if (item.type == Deleted) {
      return resizeIcon(VcsCodeReviewIcons.Delete)
    }
    return resizeIcon(SpaceIcons.Main)
  }

  private fun resizeIcon(icon: Icon) = resizeIcon(icon, avatarProvider.iconSize.get())

  private fun createUnsupportedMessageTypePanel(messageLink: String?): JComponent {
    val description = if (messageLink != null) {
      SpaceBundle.message("chat.unsupported.message.type.with.link", messageLink)
    }
    else {
      SpaceBundle.message("chat.unsupported.message.type")
    }
    return JBLabel(description, AllIcons.General.Warning, SwingConstants.LEFT).setCopyable(true)
  }

  private fun JComponent.addThreadComponentIfNeeded(message: SpaceChatItem): JComponent {
    return if (message.type is CodeDiscussion) {
      this
    }
    else {
      val threadComponent = createCollapsedThreadComponent(project, lifetime, message, null)
      JPanel(VerticalLayout(0)).apply {
        isOpaque = false
        add(this@addThreadComponentIfNeeded, VerticalLayout.FILL_HORIZONTAL)
        add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      }
    }
  }

  private fun JComponent.addStartThreadField(message: SpaceChatItem): JComponent {
    val startThreadVm = message.startThreadVm

    val isWritingFirstMessageModel = SingleValueModelImpl(false)
    startThreadVm.isWritingFirstMessage.forEach(lifetime) {
      isWritingFirstMessageModel.value = it
    }
    val emptyPanel = JPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    val statsPlace = SpaceStatsCounterCollector.SendMessagePlace.NEW_THREAD
    val firstThreadMessageField = ToggleableContainer.create(isWritingFirstMessageModel, { emptyPanel }, {
      val submittableModel = object : SubmittableTextFieldModelBase("") {
        override fun submit() {
          isBusy = true
          SpaceStatsCounterCollector.SEND_MESSAGE.log(statsPlace, false)
          launch(lifetime, Ui) {
            startThreadVm.startThread(document.text)
            isBusy = false
          }
        }
      }
      val newMessageComponent = SpaceChatNewMessageWithAvatarComponent(
        lifetime,
        SpaceChatAvatarType.THREAD,
        submittableModel,
        statsPlace,
        onCancel = { startThreadVm.stopWritingFirstMessage() }
      )
      BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.emptyTop(10)
        addToCenter(newMessageComponent)
      }
    })
    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
      add(this@addStartThreadField, VerticalLayout.FILL_HORIZONTAL)
      add(firstThreadMessageField, VerticalLayout.FILL_HORIZONTAL)
    }
  }

  private fun createSimpleMessageComponent(message: SpaceChatItem): SpaceChatMarkdownTextComponent =
    SpaceChatMarkdownTextComponent(server, message.text, message.isEdited)

  internal class Item(
    avatar: Icon,
    private val title: MessageTitleComponent?,
    header: JComponent,
    content: JComponent
  ) : HoverableJPanel() {
    companion object {
      val AVATAR_GAP: Int
        get() = JBUIScale.scale(8)
    }

    init {
      val headerPart = BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(avatar.iconWidth + AVATAR_GAP)
        addToCenter(header)
      }

      val avatarPanel = BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        addToTop(userAvatar(avatar))
      }
      val rightPart = JPanel(VerticalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(AVATAR_GAP)
        title?.let { add(it, VerticalLayout.FILL_HORIZONTAL) }
        val contentWrapper = BorderLayoutPanel().apply {
          isOpaque = false
          if (title == null) {
            border = JBUI.Borders.emptyTop(avatar.iconHeight / 4)
          }
          addToCenter(content)
        }
        add(contentWrapper, VerticalLayout.FILL_HORIZONTAL)
      }
      val messagePanel = BorderLayoutPanel().apply {
        isOpaque = false
        addToLeft(avatarPanel)
        addToCenter(rightPart)
      }

      layout = VerticalLayout(JBUI.scale(3))
      isOpaque = false
      border = JBUI.Borders.emptyTop(15)

      add(headerPart, VerticalLayout.FILL_HORIZONTAL)
      add(messagePanel, VerticalLayout.FILL_HORIZONTAL)
    }

    private fun userAvatar(avatar: Icon) = JLabel(avatar)

    override fun hoverStateChanged(isHovered: Boolean) {
      title ?: return
      title.actionsPanel.isVisible = isHovered
      title.revalidate()
      title.repaint()
    }
  }
}