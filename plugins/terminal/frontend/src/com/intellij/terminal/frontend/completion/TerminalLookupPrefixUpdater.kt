package com.intellij.terminal.frontend.completion

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.util.Key
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModel
import org.jetbrains.plugins.terminal.block.reworked.TerminalOutputModelListener

@ApiStatus.Internal
class TerminalLookupPrefixUpdater private constructor(
  private val model: TerminalOutputModel,
  private val lookup: LookupImpl,
  coroutineScope: CoroutineScope,
) {
  // Use channel to not lose any request since we have to count unhandled requests.
  private val prefixUpdateRequests = Channel<Unit>(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val pendingRequestsCount: MutableStateFlow<Int> = MutableStateFlow(0)

  init {
    coroutineScope.launch {
      model.cursorOffsetState.collect {
        pendingRequestsCount.update { it + 1 }
        prefixUpdateRequests.send(Unit)
      }
    }

    model.addListener(coroutineScope.asDisposable(), object : TerminalOutputModelListener {
      override fun afterContentChanged(model: TerminalOutputModel, startOffset: Int, isTypeAhead: Boolean) {
        pendingRequestsCount.update { it + 1 }
        prefixUpdateRequests.trySend(Unit)
      }
    })

    coroutineScope.launch(Dispatchers.UiWithModelAccess) {
      for (request in prefixUpdateRequests) {
        // Logic inside can attempt to close the lookup.
        // In this case the coroutine will be canceled.
        // But some lookup closing listeners might try to run read action and will fail.
        // Let's wrap it into a non-cancellable context to avoid such problems.
        withContext(NonCancellable) {
          updateLookupPrefix()
          pendingRequestsCount.update { it - 1 }
        }
      }
    }
  }

  private fun updateLookupPrefix() {
    syncEditorCaretWithOutputModel()

    if (lookup.isLookupDisposed) {
      return
    }

    val curPrefix = calculateCurPrefix() ?: return
    val newPrefix = calculateUpdatedPrefix()
    if (newPrefix == null) {
      // The text was changed in some way, so the lookup is not valid now. Let's close it
      closeLookupOrRestart()
      return
    }

    val commonPrefixLength = newPrefix.commonPrefixWith(curPrefix).length
    val truncateTimes = curPrefix.length - commonPrefixLength
    truncatePrefix(truncateTimes)
    val textToAppend = newPrefix.substring(commonPrefixLength, newPrefix.length)
    appendPrefix(textToAppend)

    if (!lookup.isLookupDisposed && (truncateTimes > 0 || textToAppend.isNotEmpty())) {
      CompletionServiceImpl.currentCompletionProgressIndicator?.prefixUpdated()
    }
  }

  private fun calculateCurPrefix(): String? {
    val curItem = lookup.currentItem ?: return null
    return lookup.itemPattern(curItem)
  }

  private fun calculateUpdatedPrefix(): String? {
    val startOffset = lookup.lookupStart
    val caretOffset = model.cursorOffsetState.value.toRelative()
    if (caretOffset < startOffset) {
      return null  // It looks like the lookup is not valid
    }
    return model.document.immutableCharSequence.substring(startOffset, caretOffset)
  }

  private fun truncatePrefix(times: Int) {
    val preserveSelection = CompletionServiceImpl.currentCompletionProgressIndicator?.isAutopopupCompletion != true
    val hideOffset = lookup.lookupStart
    repeat(times) {
      if (lookup.isLookupDisposed) {
        return
      }

      lookup.truncatePrefix(preserveSelection, hideOffset)
    }

    if (!lookup.isLookupDisposed && times > 0) {
      // Hide the lookup if the prefix became empty after truncation
      val curPrefix = calculateCurPrefix()
      if (curPrefix != null && curPrefix.isEmpty()) {
        lookup.hideLookup(true)
        return
      }
    }
  }

  private fun appendPrefix(text: String) {
    for (c in text) {
      if (lookup.isLookupDisposed) {
        return
      }
      lookup.appendPrefix(c)
    }
  }

  private fun closeLookupOrRestart() {
    // If the cursor was placed right before the lookup start offset, let's restart the completion
    val cursorOffset = model.cursorOffsetState.value.toRelative()
    if (cursorOffset == lookup.lookupOriginalStart - 1) {
      CompletionServiceImpl.currentCompletionProgressIndicator?.scheduleRestart()
    }
    else {
      lookup.hideLookup(true)
    }
  }

  private fun syncEditorCaretWithOutputModel() {
    if (!lookup.isLookupDisposed) {
      val cursorOffset = model.cursorOffsetState.value.toRelative()
      lookup.performGuardedChange {
        lookup.editor.caretModel.moveToOffset(cursorOffset)
      }
    }
  }

  @TestOnly
  suspend fun awaitPrefixUpdated() {
    // It is a kind of hack to await that cursorOffsetState flow is handled on our side
    // and pendingRequestsCount is incremented
    yield()

    pendingRequestsCount.first { it == 0 }
  }

  companion object {
    private val KEY: Key<TerminalLookupPrefixUpdater> = Key.create("TerminalLookupPrefixUpdater")

    @RequiresEdt
    fun install(outputModel: TerminalOutputModel, lookup: LookupImpl, coroutineScope: CoroutineScope) {
      val updater = TerminalLookupPrefixUpdater(outputModel, lookup, coroutineScope)
      lookup.putUserData(KEY, updater)
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        lookup.putUserData(KEY, null)
      }
    }

    @TestOnly
    fun get(lookup: LookupImpl): TerminalLookupPrefixUpdater? {
      return lookup.getUserData(KEY)
    }
  }
}