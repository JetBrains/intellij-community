// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.code.api.ReviewerChangedType
import circlet.platform.client.resolve
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.runWriteAction
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
import com.intellij.space.chat.ui.thread.SpaceChatReplyActionFactory
import com.intellij.space.chat.ui.thread.createThreadComponent
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.awaitLoaded
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
  private val avatarProvider: SpaceAvatarProvider
) : TimelineItemComponentFactory<SpaceChatItem> {

  private val codeDiscussionComponentFactory = SpaceChatCodeDiscussionComponentFactory(project, lifetime, server)

  private val replyActionFactory = SpaceChatReplyActionFactory()

  /**
   * Method should return [HoverableJPanel] because it is used to implement hovering properly
   *
   * @see SpaceChatPanel
   */
  override fun createComponent(item: SpaceChatItem): HoverableJPanel {
    val component =
      when (val type = item.type) {
        is CodeDiscussion ->
          codeDiscussionComponentFactory.createComponent(type.discussion, item.thread!!) ?: createUnsupportedMessageTypePanel(item.link)
        is SimpleText -> createSimpleMessageComponent(item)
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
        is MCMessage -> SpaceMCMessageComponent(server, type.block, item.attachments)
        is Unknown -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      item.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarProvider.iconSize.get()),
      MessageTitleComponent(lifetime, item),
      SpaceChatMessagePendingHeader(item),
      createEditableContent(component.addThreadComponentIfNeeded(item).addStartThreadField(item), item)
    )
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

  private fun JComponent.addThreadComponentIfNeeded(message: SpaceChatItem): JComponent {
    val thread = message.thread
    return if (thread == null || message.type is CodeDiscussion) {
      this
    }
    else {
      val threadComponent = createThreadComponent(project, lifetime, thread, replyActionFactory)
      JPanel(VerticalLayout(0)).apply {
        isOpaque = false
        add(this@addThreadComponentIfNeeded, VerticalLayout.FILL_HORIZONTAL)
        add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      }
    }
  }

  private fun JComponent.addStartThreadField(message: SpaceChatItem): JComponent {
    val startThreadVm = message.startThreadVm
    val submittableModel = object : SubmittableTextFieldModelBase("") {
      override fun submit() {
        isBusy = true
        launch(lifetime, Ui) {
          startThreadVm.startThread(document.text)
          // keep model busy because message will be fully redrawn with new thread
        }
      }
    }

    val isWritingFirstMessageModel = SingleValueModelImpl(false)
    startThreadVm.isWritingFirstMessage.forEach(lifetime) {
      isWritingFirstMessageModel.value = it
    }
    val emptyPanel = JPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    val firstThreadMessageField = ToggleableContainer.create(isWritingFirstMessageModel, { emptyPanel }, {
      SpaceChatNewMessageWithAvatarComponent(
        lifetime,
        SpaceChatAvatarType.THREAD,
        submittableModel,
        onCancel = { startThreadVm.stopWritingFirstMessage() }
      )
    })
    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
      add(this@addStartThreadField, VerticalLayout.FILL_HORIZONTAL)
      add(firstThreadMessageField, VerticalLayout.FILL_HORIZONTAL)
    }
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
      val workspace = SpaceWorkspaceComponent.getInstance().workspace.value ?: return@forEach
      runWriteAction {
        submittableModel.document.setText(workspace.completion.editable(editingVm.message.text))
      }
      editingStateModel.value = true
    }
    return ToggleableContainer.create(editingStateModel, { content }, {
      SubmittableTextField(SpaceBundle.message("chat.message.edit.action.text"), submittableModel, onCancel = { message.stopEditing() })
    })
  }

  private fun createSimpleMessageComponent(message: SpaceChatItem): SpaceChatMarkdownTextComponent =
    SpaceChatMarkdownTextComponent(server, message.text, message.isEdited)

  internal class Item(
    avatar: Icon,
    private val title: MessageTitleComponent,
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
        add(title, VerticalLayout.FILL_HORIZONTAL)
        add(content, VerticalLayout.FILL_HORIZONTAL)
      }
      val messagePanel = BorderLayoutPanel().apply {
        isOpaque = false
        addToLeft(avatarPanel)
        addToCenter(rightPart)
      }

      layout = VerticalLayout(JBUI.scale(3))
      isOpaque = false
      border = JBUI.Borders.empty(10, 0)

      add(headerPart, VerticalLayout.FILL_HORIZONTAL)
      add(messagePanel, VerticalLayout.FILL_HORIZONTAL)
    }

    private fun userAvatar(avatar: Icon) = LinkLabel<Any>("", avatar)

    override fun hoverStateChanged(isHovered: Boolean) {
      title.actionsPanel.isVisible = isHovered
      title.revalidate()
      title.repaint()
    }
  }
}