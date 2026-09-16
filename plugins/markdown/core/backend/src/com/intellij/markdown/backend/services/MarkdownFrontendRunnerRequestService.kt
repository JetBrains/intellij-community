// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.openapi.Disposable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import org.intellij.plugins.markdown.service.MarkdownFrontendRunnerRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@ApiStatus.Internal
class MarkdownFrontendRunnerRequestService : Disposable {
  private data class PendingRequest(val request: MarkdownFrontendRunnerRequest, val createdAt: TimeMark)

  private var timeSource: TimeSource = TimeSource.Monotonic
  private val requests = Channel<PendingRequest>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun requests(): Flow<MarkdownFrontendRunnerRequest> = requests.receiveAsFlow().mapNotNull { pending ->
    pending.request.takeIf { pending.createdAt.elapsedNow() < 5.seconds }
  }

  fun request(request: MarkdownFrontendRunnerRequest) {
    requests.trySend(PendingRequest(request, timeSource.markNow()))
  }

  override fun dispose() {
    requests.cancel()
  }

  companion object {
    @TestOnly
    fun createForTest(timeSource: TimeSource): MarkdownFrontendRunnerRequestService {
      return MarkdownFrontendRunnerRequestService().also { it.timeSource = timeSource }
    }
  }
}
