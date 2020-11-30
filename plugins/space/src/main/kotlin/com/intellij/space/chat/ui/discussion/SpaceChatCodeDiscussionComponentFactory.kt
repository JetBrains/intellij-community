// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionAddedFeedEvent
import circlet.code.api.CodeDiscussionSnippet
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.property
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.space.chat.ui.processItemText
import com.intellij.space.chat.ui.thread.createThreadComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.codereview.SingleValueModelImpl
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.reactive.Property
import runtime.reactive.map
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
    val resolved = lifetime.map(event.codeDiscussion.property()) { it.resolved }
    val collapseButton = InlineIconButton(AllIcons.General.CollapseComponent, AllIcons.General.CollapseComponentHover)
    val expandButton = InlineIconButton(AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover)

    val fileNameComponent = JBUI.Panels.simplePanel(
      createFileNameComponent(discussion.anchor.filename!!, collapseButton, expandButton, resolved)
    ).apply {
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
      border = RoundedLineBorder(UIUtil.CONTRAST_BORDER_COLOR, IdeBorderFactory.BORDER_ROUNDNESS)
      add(fileNameComponent)
      add(diffEditorComponent, VerticalLayout.FILL_HORIZONTAL)
    }
    val reviewCommentComponent = HtmlEditorPane()

    val panel = JPanel(VerticalLayout(UI.scale(4))).apply {
      isOpaque = false
      add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
      add(reviewCommentComponent, VerticalLayout.FILL_HORIZONTAL)
    }

    thread.mvms.forEach(lifetime) {
      val comment = it.messages.first()
      reviewCommentComponent.setBody(processItemText(server, comment.message.text))
      reviewCommentComponent.repaint()
    }
    val threadComponent = createThreadComponent(project, lifetime, thread, withFirst = false)

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(panel, VerticalLayout.FILL_HORIZONTAL)
      add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      addDiscussionExpandCollapseHandling(
        this,
        listOf(threadComponent, reviewCommentComponent, diffEditorComponent),
        collapseButton,
        expandButton,
        resolved
      )
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
      add(nameLabel)

      if (parentPath.isNotBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
          setCopyable(true)
        })
      }
      add(resolvedLabel, CC().hideMode(3))
      add(collapseButton, CC().hideMode(3))
      add(expandButton, CC().hideMode(3))

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
    componentsToHide: Collection<JComponent>,
    collapseButton: InlineIconButton,
    expandButton: InlineIconButton,
    resolved: Property<Boolean>
  ) {
    val collapseModel = SingleValueModelImpl(true)
    collapseButton.actionListener = ActionListener { collapseModel.value = true }
    expandButton.actionListener = ActionListener { collapseModel.value = false }

    resolved.forEach(lifetime) {
      collapseModel.value = it
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
    stateUpdated(true)
  }
}