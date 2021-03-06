// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionRecord
import circlet.code.api.CodeDiscussionSnippet
import circlet.platform.api.Ref
import circlet.platform.client.property
import circlet.platform.client.resolve
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.ui.SpaceChatAvatarType
import com.intellij.space.chat.ui.SpaceChatEditableComponent
import com.intellij.space.chat.ui.SpaceChatMarkdownTextComponent
import com.intellij.space.chat.ui.SpaceChatNewMessageWithAvatarComponent
import com.intellij.space.chat.ui.message.createResolvedComponent
import com.intellij.space.chat.ui.thread.createCollapsedThreadComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.codereview.SingleValueModel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.Ui
import runtime.reactive.Property
import runtime.reactive.property.map
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SpaceChatCodeDiscussionComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
) {
  fun createComponent(message: SpaceChatItem, codeDiscussion: Ref<CodeDiscussionRecord>): JComponent? {
    val discussionMessage = message.projectedItem ?: return null
    val discussion = codeDiscussion.resolve()
    val discussionProperty = codeDiscussion.property()
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

    val collapseModel = SingleValueModelImpl(true)
    val panel = JPanel(VerticalLayout(JBUIScale.scale(4))).apply {
      isOpaque = false
      add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
      add(createEditableReviewCommentComponent(reviewCommentComponent, message, collapseModel), VerticalLayout.FILL_HORIZONTAL)
    }

    val outerReplyComponent = createOuterReplyComponent(discussionProperty, message)
    val threadComponent = createThreadComponent(discussionProperty, message)
    val component = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(panel, VerticalLayout.FILL_HORIZONTAL)
      add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      add(outerReplyComponent, VerticalLayout.FILL_HORIZONTAL)
      addDiscussionExpandCollapseHandling(
        this,
        collapseModel,
        listOf(outerReplyComponent, threadComponent, reviewCommentComponent, diffEditorComponent),
        collapseButton,
        expandButton,
        resolved
      )
    }

    discussionMessage.forEach(lifetime) {
      reviewCommentComponent.setMarkdownText(it.text, it.edited != null)
      reviewCommentComponent.repaint()
    }

    return component
  }

  private fun createOuterReplyComponent(discussion: Property<CodeDiscussionRecord>, message: SpaceChatItem): JComponent {
    val newMessageStateModel = SingleValueModelImpl(false)
    val replyAction = ActionLink(SpaceBundle.message("chat.reply.action")) {
      newMessageStateModel.value = true
    }
    val resolveComponent = createResolveComponent(lifetime, message.chat.client, discussion)
    val actionsComponent = JPanel(HorizontalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      border = JBUI.Borders.emptyTop(8)
      add(replyAction)
      add(resolveComponent)
    }
    val outerReplyComponent = ToggleableContainer.create(
      newMessageStateModel,
      mainComponentSupplier = { actionsComponent },
      toggleableComponentSupplier = {
        val statsPlace = SpaceStatsCounterCollector.SendMessagePlace.FIRST_DISCUSSION_ANSWER
        val submittableModel = object : SubmittableTextFieldModelBase("") {
          override fun submit() {
            val isPending = false
            SpaceStatsCounterCollector.SEND_MESSAGE.log(statsPlace, isPending)
            launch(lifetime, Ui) {
              val thread = message.loadThread(lifetime)
              thread?.sendMessage(document.text, pending = isPending)
              runWriteAction {
                document.setText("")
              }
              newMessageStateModel.value = false
            }
          }
        }
        SpaceChatNewMessageWithAvatarComponent(lifetime, SpaceChatAvatarType.THREAD, submittableModel, statsPlace,
                                               onCancel = {
                                                 newMessageStateModel.value = false
                                               })
      }
    )
    message.threadPreview!!.messageCount.forEach(lifetime) { count ->
      actionsComponent.isVisible = count == 0
    }
    return BorderLayoutPanel().apply {
      isOpaque = false
      addToCenter(outerReplyComponent)
    }
  }

  private fun createThreadComponent(discussionProperty: Property<CodeDiscussionRecord>, message: SpaceChatItem): JComponent {
    val threadActionsFactory = SpaceChatDiscussionActionsFactory(
      lifetime,
      message.chat.client,
      discussionProperty
    )
    return BorderLayoutPanel().apply {
      isOpaque = false
      addToCenter(createCollapsedThreadComponent(project, lifetime, message, threadActionsFactory, withFirst = false))
    }
  }

  private fun createEditableReviewCommentComponent(
    reviewCommentContent: JComponent,
    message: SpaceChatItem,
    collapseModel: SingleValueModel<Boolean>
  ): JComponent {
    val editableReviewCommentComponent = SpaceChatEditableComponent(project, lifetime, reviewCommentContent, message)
    editableReviewCommentComponent.editingModel.addValueUpdatedListener { isEditing ->
      if (isEditing) {
        collapseModel.value = false
      }
    }
    return editableReviewCommentComponent
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

    val resolvedLabel = createResolvedComponent(lifetime, resolved)
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
      SpaceStatsCounterCollector.COLLAPSE_DISCUSSION.log(project)
    }
    expandButton.actionListener = ActionListener {
      collapseModel.value = false
      SpaceStatsCounterCollector.EXPAND_DISCUSSION.log(project)
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