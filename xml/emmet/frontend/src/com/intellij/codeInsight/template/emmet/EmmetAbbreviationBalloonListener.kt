// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet

import com.intellij.codeInsight.template.emmet.rpc.ShowAbbreviationBaloonUiEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener

internal class EmmetAbbreviationBalloonListener : ProjectRemoteTopicListener<ShowAbbreviationBaloonUiEvent> {
  override val topic = com.intellij.codeInsight.template.emmet.rpc.EmmetAbbreviationBaloonTopic.TOPIC
  override fun handleEvent(
    project: Project,
    event: ShowAbbreviationBaloonUiEvent,
  ) = invokeLater { EmmetAbbreviationBaloonUi.showBaloon(event) }
}
