package com.intellij.space.chat

import circlet.client.api.*
import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.CodeDiscussionSnippet
import circlet.completion.mentions.MentionConverter
import circlet.m2.ChannelsVm
import circlet.m2.M2ChannelMode
import circlet.m2.M2MessageVm
import circlet.m2.channel.M2ChannelVm
import circlet.platform.api.Ref
import circlet.platform.api.format
import circlet.platform.api.isTemporary
import circlet.platform.client.resolve
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.Ui
import runtime.date.DateFormat
import runtime.reactive.awaitTrue
import java.awt.BorderLayout
import javax.swing.*

internal fun createSpaceChatPanel(
  project: Project,
  lifetime: Lifetime,
  parent: Disposable,
  channelsVm: ChannelsVm,
  chatRecord: Ref<M2ChannelRecord>
): JPanel {
  val timeline = JPanel(VerticalLayout(UI.scale(20))).apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(6)
  }
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
    val avatarSize = JBValue.UIInteger("space.chat.avatar.size", 30)
    val avatarProvider = SpaceAvatarProvider(lifetime, timeline, avatarSize)
    val server = channelsVm.client.server
    val items = messages.value.messages.mapNotNull { messageViewModel ->
      val message = messageViewModel.message
      val details = message.details ?: throw IllegalStateException()
      when (details) {
        is CodeDiscussionAddedFeedEvent -> {
          val discussion = details.codeDiscussion.resolve()
          val codeDiscussionChat = channelsVm.channel(lifetime, discussion.channel, mode = M2ChannelMode.CodeDiscussion())
          codeDiscussionChat.awaitFullLoad(lifetime)
          val codeDiscussionMessages = codeDiscussionChat.mvms.prop.value.messages.map { it.message }
          createDiff(project, discussion, codeDiscussionMessages.first(), server)
            ?.withThread(lifetime, server, codeDiscussionMessages.drop(1))
        }
        is M2TextItemContent -> {
          val messagePanel = createSimpleMessagePanel(message, server)
          val thread = message.thread
          if (thread == null) {
            messagePanel
          }
          else {
            val threadChat = channelsVm.channel(lifetime, thread)
            threadChat.awaitFullLoad(lifetime)
            messagePanel.withThread(lifetime, server, threadChat.mvms.value.messages.map { it.message })
          }
        }
        else -> createUnsupportedMessageTypePanel(messageViewModel.getLink(server))
      }?.let { component ->
        Item(
          message.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarSize.get()),
          createMessageTitle(server, message),
          component
        )
      }
    }
    centerPanel.stopLoading()
    items.forEach { timeline.add(it, VerticalLayout.FILL_HORIZONTAL) }
    timeline.revalidate()
    timeline.repaint()
  }
  return JBUI.Panels.simplePanel(centerPanel).apply {
    isOpaque = true
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }
}

private suspend fun M2ChannelVm.awaitFullLoad(lifetime: Lifetime) {
  ready.awaitTrue(lifetime)
  val messages = mvms.prop
  while (messages.value.hasPrev) {
    loadPrev()
  }
  while (messages.value.hasNext) {
    loadNext()
  }
}

private fun M2MessageVm.getLink(hostUrl: String): String? =
  if (canCopyLink && !message.isTemporary()) {
    Navigator.im.message(channelVm.key.value, message).absoluteHref(hostUrl)
  }
  else {
    null
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

private fun createMessageTitle(server: String, message: ChannelItemRecord): JComponent {
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

private fun JComponent.withThread(lifetime: Lifetime, server: String, messages: List<ChannelItemRecord>): JComponent {
  return JPanel(VerticalLayout(JBUI.scale(10))).also { panel ->
    panel.isOpaque = false
    val threadAvatarsProvider = SpaceAvatarProvider(lifetime, panel, JBValue.UIInteger("space.chat.thread.avatar.size", 20))
    panel.add(this, VerticalLayout.FILL_HORIZONTAL)
    messages.forEach { message ->
      panel.add(
        Item(
          threadAvatarsProvider.getIcon(message.author.asUser!!),
          createMessageTitle(server, message),
          createSimpleMessagePanel(message, server)
        ),
        VerticalLayout.FILL_HORIZONTAL
      )
    }
  }
}

private fun createDiff(
  project: Project,
  discussion: CodeDiscussionRecord,
  comment: ChannelItemRecord,
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

  return JPanel(VerticalLayout(UI.scale(4))).apply {
    isOpaque = false
    add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
    add(createSimpleMessagePanel(comment, server))
  }
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

private fun createSimpleMessagePanel(message: ChannelItemRecord, server: String) = HtmlEditorPane().apply {
  setBody(MentionConverter.html(message.text, server))
}


// TODO: reuse this part from GitHub plugin
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