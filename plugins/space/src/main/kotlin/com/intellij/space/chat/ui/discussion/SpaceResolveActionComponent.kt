// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.discussion

import circlet.code.api.CodeDiscussionRecord
import circlet.code.codeReview
import circlet.platform.client.KCircletClient
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.SingleValueModel
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.delay
import libraries.coroutines.extra.launch
import runtime.Ui
import runtime.reactive.Property
import javax.swing.JComponent
import javax.swing.JLabel

internal fun createResolveComponent(
  lifetime: Lifetime,
  client: KCircletClient,
  discussion: Property<CodeDiscussionRecord>,
): JComponent {
  val resolvingLabel = JLabel(SpaceBundle.message("chat.resolving.action.state")).apply {
    foreground = UIUtil.getContextHelpForeground()
  }
  val reopeningLabel = JLabel(SpaceBundle.message("chat.reopening.action.state")).apply {
    foreground = UIUtil.getContextHelpForeground()
  }
  val resolvingModel = SingleValueModelImpl(ResolvingState.READY)
  val resolveReopenLabel = createResolveReopenLabel(lifetime, client, discussion, resolvingModel)
  val contentPanel = BorderLayoutPanel().apply {
    isOpaque = false
    addToCenter(resolveReopenLabel)
  }
  resolvingModel.addValueUpdatedListener { newState ->
    val stateLabel = when (newState) {
      ResolvingState.RESOLVING -> resolvingLabel
      ResolvingState.REOPENING -> reopeningLabel
      ResolvingState.READY -> resolveReopenLabel
    }
    contentPanel.removeAll()
    contentPanel.addToCenter(stateLabel)
    contentPanel.revalidate()
    contentPanel.repaint()
  }
  return contentPanel
}

private fun createResolveReopenLabel(
  lifetime: Lifetime,
  client: KCircletClient,
  discussion: Property<CodeDiscussionRecord>,
  resolvingModel: SingleValueModel<ResolvingState>
): JComponent {
  val reviewService = client.codeReview
  fun resolve() {
    val currentDiscussion = discussion.value
    launch(lifetime, Ui) {
      if (!currentDiscussion.resolved) {
        SpaceStatsCounterCollector.RESOLVE_DISCUSSION.log()
        resolvingModel.value = ResolvingState.RESOLVING
      }
      else {
        SpaceStatsCounterCollector.REOPEN_DISCUSSION.log()
        resolvingModel.value = ResolvingState.REOPENING
      }
      delay(200) // reduce status label blinking
      reviewService.resolveCodeDiscussion(currentDiscussion.id, !currentDiscussion.resolved)
      resolvingModel.value = ResolvingState.READY
    }
  }

  val label = ActionLink(SpaceBundle.message("chat.resolve.action")) {
    resolve()
  }
  discussion.forEach(lifetime) {
    label.text = if (it.resolved) {
      SpaceBundle.message("chat.reopen.action")
    }
    else {
      SpaceBundle.message("chat.resolve.action")
    }
  }

  return label
}

private enum class ResolvingState {
  RESOLVING,
  REOPENING,
  READY
}