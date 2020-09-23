package com.intellij.space.chat.ui

import circlet.client.api.*
import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.CodeDiscussionSnippet
import circlet.completion.mentions.MentionConverter
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.format
import circlet.platform.client.resolve
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.SpaceChatItem
import com.intellij.space.chat.model.SpaceChatItem.Companion.convertToChatItem
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.timeline.TimelineComponent
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.Ui
import runtime.date.DateFormat
import javax.swing.*

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String
) : TimelineItemComponentFactory<SpaceChatItem> {
  // TODO: avatarProvider shouldn't require component in constructor?
  lateinit var avatarProvider: SpaceAvatarProvider

  override fun createComponent(item: SpaceChatItem): JComponent {
    val component =
      when (val details = item.details) {
        is CodeDiscussionAddedFeedEvent -> {
          val discussion = details.codeDiscussion.resolve()
          createDiff(project, discussion, item.thread!!, server)
            ?.withThread(lifetime, server, item.thread, withFirst = false) ?: createUnsupportedMessageTypePanel(item.link)
        }
        is M2TextItemContent -> {
          val messagePanel = createSimpleMessagePanel(item, server)
          val thread = item.thread
          if (thread == null) {
            messagePanel
          }
          else {
            messagePanel.withThread(lifetime, server, thread)
          }
        }
        else -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      item.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarProvider.iconSize.get()),
      createMessageTitle(server, item),
      component
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

  private fun createMessageTitle(server: String, message: SpaceChatItem): JComponent {
    val authorPanel = HtmlEditorPane().apply {
      setBody(createMessageAuthorChunk(message.author, server).bold().toString())
    }
    val timePanel = HtmlEditorPane().apply {
      foreground = UIUtil.getContextHelpForeground()
      setBody(HtmlChunk.text(message.created.format(DateFormat.HOURS_AND_MINUTES)).toString()) // NON-NLS
    }
    return JPanel(HorizontalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      add(authorPanel)
      add(timePanel)
    }
  }

  private fun createMessageAuthorChunk(author: CPrincipal, server: String): HtmlChunk =
    when (val details = author.details) {
      is CUserPrincipalDetails -> {
        val user = details.user.resolve()
        HtmlChunk.link(Navigator.m.member(user.username).absoluteHref(server), user.name.fullName()) // NON-NLS
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

  private fun JComponent.withThread(lifetime: Lifetime, server: String, thread: M2ChannelVm, withFirst: Boolean = true): JComponent {
    return JPanel(VerticalLayout(JBUI.scale(10))).also { panel ->
      panel.isOpaque = false
      val itemsListModel = SpaceChatItemListModel()
      val itemComponentFactory = SpaceChatItemComponentFactory(project, lifetime, server)
      val threadTimeline = TimelineComponent(itemsListModel, itemComponentFactory)
      val threadAvatarSize = JBValue.UIInteger("space.chat.thread.avatar.size", 20)
      itemComponentFactory.avatarProvider = SpaceAvatarProvider(lifetime, threadTimeline, threadAvatarSize)

      // TODO: don't subscribe on thread changes in factory
      thread.mvms.forEach(lifetime) { messageList ->
        launch(lifetime, Ui) {
          itemsListModel.messageListUpdated(
            messageList.messages
              .drop(if (withFirst) 0 else 1)
              .map { it.message.convertToChatItem(it.getLink(server)) }
          )
        }
      }
      panel.add(this, VerticalLayout.FILL_HORIZONTAL)
      panel.add(threadTimeline, VerticalLayout.FILL_HORIZONTAL)
    }
  }

  private fun createDiff(
    project: Project,
    discussion: CodeDiscussionRecord,
    thread: M2ChannelVm,
    server: String
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
      panel.add(createSimpleMessagePanel(comment.message.convertToChatItem(comment.getLink(server)), server))
    }
    return panel
  }

  private fun createFileNameComponent(filePath: String): JComponent {
    val name = PathUtil.getFileName(filePath)
    val parentPath = PathUtil.getParentPath(filePath)
    val nameLabel = JLabel(name, null, SwingConstants.LEFT)

    return NonOpaquePanel(HorizontalLayout(JBUI.scale(5))).apply {
      add(nameLabel)

      if (!parentPath.isBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
        })
      }
    }
  }

  private fun createSimpleMessagePanel(message: SpaceChatItem, server: String) = HtmlEditorPane().apply {
    setBody(MentionConverter.html(message.text, server))
  }

  private class Item(avatar: Icon, title: JComponent, content: JComponent? = null) : JPanel() {
    init {
      val avatarLabel = userAvatar(avatar)
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", JBUI.scale(5).toString())
                           .insets("0", "0", "0", "0")
                           .fill()).apply {
        columnConstraints = "[]${UI.scale(8)}[]"
      }

      add(avatarLabel, CC().pushY().spanY(2).alignY("top"))
      add(title, CC().pushX().alignY("top"))
      if (content != null) {
        add(content, CC().newline().grow().push())
      }
    }

    private fun userAvatar(avatar: Icon) = LinkLabel<Any>("", avatar)
  }
}