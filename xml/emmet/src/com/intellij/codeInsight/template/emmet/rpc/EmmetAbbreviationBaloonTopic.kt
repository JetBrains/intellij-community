// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet.rpc

import com.intellij.codeInsight.template.CustomTemplateCallback
import com.intellij.codeInsight.template.emmet.EmmetAbbreviationBalloon
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditorOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.broadcast
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Command we are sending to the frontend to show UI
 */
@Serializable
data class ShowAbbreviationBaloonUiEvent(
  val transactionId: Int,
  val editorId: EditorId,
  val historyKey: String,
  val lastAbbreviationKey: String,
  val linkText: @Tooltip String?,
  val linkUrl: String?,
  val description: @Tooltip String,
) {
  fun editor(): Editor? = editorId.findEditorOrNull()

  fun project(): Project? = editorId.findEditorOrNull()?.project

}

/**
 * The context we preserve in the editor to handle response. For now we assume that only one action can be performed in the editor at a time.
 */
data class ShowAbbreviationBaloonUiContext(
  val event: ShowAbbreviationBaloonUiEvent,
  val callback: EmmetAbbreviationBalloon.Callback,
  val customTemplateCallback: CustomTemplateCallback,
)

object EmmetAbbreviationBaloonTopic {
  private val log = logger<EmmetAbbreviationBaloonTopic>()

  val TOPIC: ProjectRemoteTopic<ShowAbbreviationBaloonUiEvent> =
    ProjectRemoteTopic("emmet.showAbbreviationBaloon", ShowAbbreviationBaloonUiEvent.serializer())

  private val BALLOON_CONTEXT_KEY: Key<ShowAbbreviationBaloonUiContext> = Key.create("EmmetAbbreviationBalloonContext")

  private val transactionIdCounter: AtomicInteger = AtomicInteger(0)

  @JvmStatic
  fun nextTransactionId(): Int = transactionIdCounter.getAndIncrement()

  fun invocationContext(transactionId: Int, editorId: EditorId): ShowAbbreviationBaloonUiContext? {
    val editor = editorId.findEditorOrNull() ?: run {
      log.warn("Cannot find editor for id: $editorId")
      return null
    }

    if (editor.isDisposed()) return null

    val storedContext = editor.getUserData(BALLOON_CONTEXT_KEY) ?: run {
      log.warn("Cannot find context for transactionId: $transactionId, editorId: $editorId")
      return null
    }

    return if (storedContext.event.transactionId == transactionId) storedContext
    else run {
      log.warn("Transaction id mismatch, expected $transactionId, got ${storedContext.event.transactionId} editorId: $editorId")
      null
    }
  }

  fun dropInvocationContext(transactionId: Int, editorId: EditorId) {
    editorId.findEditorOrNull()?.putUserData(BALLOON_CONTEXT_KEY, null)
  }

  @JvmStatic
  fun invokeUi(
    event: ShowAbbreviationBaloonUiEvent,
    callback: EmmetAbbreviationBalloon.Callback,
    customTemplateCallback: CustomTemplateCallback,
  ) {
    val project = customTemplateCallback.editor.project ?: run {
      log.warn("Cannot find project for editor: ${customTemplateCallback.editor}")
      return
    }
    customTemplateCallback.editor.putUserData(BALLOON_CONTEXT_KEY, ShowAbbreviationBaloonUiContext(event, callback, customTemplateCallback))
    TOPIC.broadcast(project, event)
  }
}
