// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class MarkdownUpdateHandler {

  abstract val requests: Flow<PreviewRequest>
  protected abstract fun addRequest(request: PreviewRequest): Boolean

  fun setContent(content: String, initialScrollOffset: Int, document: VirtualFile?) {
    doRequest(PreviewRequest.Update(content, initialScrollOffset, document))
  }

  fun reloadWithOffset(offset: Int) {
    doRequest(PreviewRequest.ReloadWithOffset(offset))
  }

  private fun doRequest(request: PreviewRequest) {
    check(addRequest(request))
  }

  sealed interface PreviewRequest {
    data class Update(
      val content: String,
      val initialScrollOffset: Int,
      val document: VirtualFile?
    ) : PreviewRequest

    data class ReloadWithOffset(val offset: Int) : PreviewRequest
  }

  @OptIn(FlowPreview::class)
  class Debounced(private val debounceTimeout: Duration = 20.milliseconds) : MarkdownUpdateHandler() {

    private val _updateViewRequests = MutableSharedFlow<PreviewRequest>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val requests: Flow<PreviewRequest>
      get() = _updateViewRequests.debounce(debounceTimeout)

    override fun addRequest(request: PreviewRequest): Boolean = _updateViewRequests.tryEmit(request)
  }
}
