// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.code.api.CodeDiscussionSnippet
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.property
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatMarkdownTextComponent
import com.intellij.space.chat.ui.thread.createThreadComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.codereview.SingleValueModel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.reactive.Property
import runtime.reactive.property.map
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class SpaceChatCodeDiscussionComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
) {
  fun createComponent(event: CodeDiscussionAddedFeedEvent, thread: M2ChannelVm): JComponent? {
    val discussion = event.codeDiscussion.resolve()
    val discussionProperty = event.codeDiscussion.property()
    val resolved = lifetime.map(discussionProperty) { it.resolved }
    val collapseButton = InlineIconButton(AllIcons.General.CollapseComponent, AllIcons.General.CollapseComponentHover)
    val expandButton = InlineIconButton(AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover)

    val filePath = discussion.anchor.filename ?: return null
    val fileNameComponent = JBUI.Panels.simplePanel(
      createFileNameComponent(filePath, collapseButton, expandButton, resolved)
    ).apply {
      isOpaque = false
      border = JBUI.Borders.empty(8)
    }
    val snippet = discussion.snippet ?: return null
    val diffEditorComponent = when (snippet) {
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
      border = RoundedLineBorder(UIUtil.CONTRAST_BORDER_COLOR, IdeBorderFactory.BORDER_ROUNDNESS)
      add(fileNameComponent)
      add(diffEditorComponent, VerticalLayout.FILL_HORIZONTAL)
    }
    val reviewCommentComponent = SpaceChatMarkdownTextComponent(server)

    val panel = JPanel(VerticalLayout(JBUIScale.scale(4))).apply {
      isOpaque = false
      add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
      add(reviewCommentComponent, VerticalLayout.FILL_HORIZONTAL)
    }

    val threadActionsFactory = SpaceChatDiscussionActionsFactory(
      discussionProperty,
      withOffset = true,
      avatarType = SpaceChatAvatarType.THREAD
    )
    val threadComponent = createThreadComponent(project, lifetime, thread, threadActionsFactory, withFirst = false)
    val outerActionsPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyTop(JBUIScale.scale(4))
      val outerActionsFactory = SpaceChatDiscussionActionsFactory(
        discussionProperty,
        withOffset = false,
        avatarType = SpaceChatAvatarType.THREAD
      )
      addToCenter(outerActionsFactory.createActionsComponent(thread))
    }
    val collapseModel = SingleValueModelImpl(true)

    val component = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(panel, VerticalLayout.FILL_HORIZONTAL)
      add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      add(outerActionsPanel, VerticalLayout.FILL_HORIZONTAL)
      addDiscussionExpandCollapseHandling(
        this,
        collapseModel,
        listOf(threadComponent, reviewCommentComponent, diffEditorComponent, outerActionsPanel),
        collapseButton,
        expandButton,
        resolved
      )
    }

    collapseModel.addValueUpdatedListener { collapsed ->
      if (!collapsed) {
        val messagesCount = thread.mvms.value.messages.size
        updateActionsComponentsVisibility(threadComponent, outerActionsPanel, component, null, messagesCount)
      }
    }

    thread.mvms.forEachWithPrevious(lifetime) { prev, new ->
      val messages = new.messages
      val comment = messages.first()
      reviewCommentComponent.setMarkdownText(comment.message.text, comment.message.edited != null)
      reviewCommentComponent.repaint()
      if (!collapseModel.value) {
        updateActionsComponentsVisibility(threadComponent, outerActionsPanel, component, prev?.messages?.size, messages.size)
      }
    }

    return component
  }

  private fun updateActionsComponentsVisibility(
    threadComponent: JComponent,
    outerActionsPanel: JComponent,
    parent: JComponent,
    prevMessagesCount: Int?,
    newMessagesCount: Int
  ) {
    if (newMessagesCount == 1) {
      threadComponent.isVisible = false
      outerActionsPanel.isVisible = true
      parent.revalidate()
      parent.repaint()
    }
    else {
      if (prevMessagesCount == null || prevMessagesCount == 1) {
        // update only for 1 -> 1+ messages count change
        threadComponent.isVisible = true
        outerActionsPanel.isVisible = false
        parent.revalidate()
        parent.repaint()
      }
    }
  }

  private fun createFileNameComponent(
    filePath: String,
    collapseButton: InlineIconButton,
    expandButton: InlineIconButton,
    resolved: Property<Boolean>
  ): JComponent {
    val name = PathUtil.getFileName(filePath)
    val parentPath = PathUtil.getParentPath(filePath)
    val nameLabel = JBLabel(name, null, SwingConstants.LEFT).setCopyable(true)

    val resolvedLabel = JBLabel(SpaceBundle.message("chat.message.resolved.text"), UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
      isOpaque = true
    }

    return NonOpaquePanel(MigLayout(LC().insets("0").gridGap("${JBUI.scale(5)}", "0").fill().noGrid())).apply {
      add(nameLabel, CC().minWidth("0").shrinkPrio(9))

      if (parentPath.isNotBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
          setCopyable(true)
        }, CC().minWidth("0").shrinkPrio(10))
      }
      add(resolvedLabel, CC().hideMode(3).shrinkPrio(0))
      add(collapseButton, CC().hideMode(3).shrinkPrio(0))
      add(expandButton, CC().hideMode(3).shrinkPrio(0))

      resolved.forEach(lifetime) {
        resolvedLabel.isVisible = it
        revalidate()
        repaint()
      }
    }
  }

  // TODO: Extract it to code-review module
  private fun addDiscussionExpandCollapseHandling(
    parentComponent: JComponent,
    collapseModel: SingleValueModel<Boolean>,
    componentsToHide: Collection<JComponent>,
    collapseButton: InlineIconButton,
    expandButton: InlineIconButton,
    resolved: Property<Boolean>
  ) {
    collapseButton.actionListener = ActionListener {
      collapseModel.value = true
    }
    expandButton.actionListener = ActionListener {
      collapseModel.value = false
    }

    fun stateUpdated(collapsed: Boolean) {
      collapseButton.isVisible = resolved.value && !collapsed
      expandButton.isVisible = resolved.value && collapsed

      val areComponentsVisible = !resolved.value || !collapsed
      componentsToHide.forEach { it.isVisible = areComponentsVisible }
      parentComponent.revalidate()
      parentComponent.repaint()
    }

    collapseModel.addValueUpdatedListener {
      stateUpdated(it)
    }

    resolved.forEach(lifetime) {
      collapseModel.value = it
    }
  }
}