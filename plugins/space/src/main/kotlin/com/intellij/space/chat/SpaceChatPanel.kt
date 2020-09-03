package com.intellij.space.chat

import circlet.client.api.ChannelItemRecord
import circlet.client.api.M2ChannelRecord
import circlet.client.api.M2TextItemContent
import circlet.client.api.Navigator
import circlet.client.api.mc.MCMessage
import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.code.api.CodeDiscussionSnippet
import circlet.completion.mentions.MentionConverter
import circlet.m2.ChannelsVm
import circlet.platform.api.Ref
import circlet.platform.api.format
import circlet.platform.client.resolve
import circlet.principals.asUser
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.*
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.date.DateFormat
import runtime.reactive.awaitTrue
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.HyperlinkEvent

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
    add(ScrollPaneFactory.createScrollPane(
      contentPanel,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ))
  }
  launch(lifetime, Ui) {
    val chatViewModel = channelsVm.channel(lifetime, chatRecord)
    chatViewModel.ready.awaitTrue(lifetime)
    val messages = chatViewModel.mvms.prop
    messages.forEach(lifetime) { messagesViewModel ->
      // load all messages under loading panel
      if (messagesViewModel.hasPrev) {
        launch(lifetime, Ui) {
          chatViewModel.loadPrev()
        }
      }
      else {
        centerPanel.stopLoading()
        val avatarProvider = SpaceAvatarProvider(lifetime, timeline, JBValue.UIInteger("space.chat.avatar.size", 30))
        messages.value.messages.forEach { messageViewModel ->
          val message = messageViewModel.message
          val details = message.details ?: throw IllegalStateException()
          val component: JComponent? = when (details) {
            is CodeDiscussionAddedFeedEvent -> createDiff(project, details)
            is M2TextItemContent -> createSimpleMessagePanel(message)
            is MCMessage -> null
            else -> null
          }
          if (component != null) {
            timeline.add(
              Item(
                avatarProvider.getIcon(message.author.asUser!!),
                createMessageTitle(channelsVm.client.server, message),
                component
              ),
              VerticalLayout.FILL_HORIZONTAL
            )
          }
        }
        timeline.revalidate()
        timeline.repaint()
      }
    }
  }
  return JBUI.Panels.simplePanel(centerPanel)
}

private fun createMessageTitle(server: String, message: ChannelItemRecord): JComponent = HtmlEditorPane().apply {
  foreground = UIUtil.getContextHelpForeground()
  setBody(
    HtmlBuilder()
      .appendLink(Navigator.m.member(message.author.asUser!!.username).absoluteHref(server), message.author.name) // NON-NLS
      .append(HtmlChunk.nbsp())
      .append(message.created.format(DateFormat.HOURS_AND_MINUTES)) // NON-NLS
      .toString()
  )
}

private fun createDiff(project: Project, codeDiscussionEvent: CodeDiscussionAddedFeedEvent): JComponent? {
  val discussion = codeDiscussionEvent.codeDiscussion.resolve()
  val diffEditorComponent = when (val snippet = discussion.snippet!!) {
    is CodeDiscussionSnippet.PlainSnippet -> {
      return null
    }
    is CodeDiscussionSnippet.InlineDiffSnippet -> createDiffComponent(project, discussion.anchor, snippet)
  }
  val fileNameComponent = createFileNameComponent(discussion.anchor.filename!!)
  return JPanel(VerticalLayout(UI.scale(4))).apply {
    add(fileNameComponent)
    add(diffEditorComponent, VerticalLayout.FILL_HORIZONTAL)
  }
}

private fun createFileNameComponent(filePath: String): JComponent {
  val name = PathUtil.getFileName(filePath)
  val parentPath = PathUtil.getParentPath(filePath)
  val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)

  val nameLabel = JLabel(name, fileType.icon, SwingConstants.LEFT)

  return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("${UI.scale(5)}", "0").fill().noGrid())).apply {
    add(nameLabel)

    if (!parentPath.isBlank()) {
      add(JLabel(parentPath).apply {
        foreground = UIUtil.getContextHelpForeground()
      })
    }
  }
}

private fun createSimpleMessagePanel(message: ChannelItemRecord) = object : HtmlPanel() {
  @Nls
  private val body = MentionConverter.html(message.text) // NON-NLS

  override fun hyperlinkUpdate(e: HyperlinkEvent?) {
    BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e)
  }

  init {
    update()
  }

  override fun getBody() = body
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