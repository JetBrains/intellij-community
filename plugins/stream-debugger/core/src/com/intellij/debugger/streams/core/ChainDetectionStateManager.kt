// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.psi.impl.DebuggerPositionResolverImpl
import com.intellij.lang.Language
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiElement
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

internal class StreamDebuggerSessionLifecycleListener(private val project: Project) : XDebuggerManagerListener {
  override fun processStarted(debugProcess: XDebugProcess) {
    ChainDetectionStateManager.getInstance(project).onProcessStarted(debugProcess.session)
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    ChainDetectionStateManager.getInstance(project).onProcessStopped(debugProcess.session)
  }
}

private val positionResolver = DebuggerPositionResolverImpl()

/**
 * Sentinel for an already finished session.
 * It is a [StateFlow] rather than a `flowOf(...)` on purpose: subscribers await a value with `first { ... }` and rely on
 * the flow not completing but staying alive until their own scope is canceled (see `LinqInlayDisplay` in Rider).
 */
private val SESSION_FINISHED: StateFlow<ChainDetectionState> =
  MutableStateFlow(ChainDetectionState(null, null, ChainStatus.NotFound))

/**
 * Tracks chain detection state per debug session as a shared re-computable flow (existence + traceability from the current execution position).
 */
@Service(Service.Level.PROJECT)
class ChainDetectionStateManager(private val cs: CoroutineScope) {
  private val sessionStates = ConcurrentHashMap<XDebugSession, SessionState>()

  fun chainStateFlow(session: XDebugSession): Flow<ChainDetectionState> = sessionStates[session]?.status ?: SESSION_FINISHED

  fun inlayStateFlow(session: XDebugSession): Flow<StreamChainInlayState> =
    chainStateFlow(session).map { state ->
      val ctx = state.suspendContext
      val position = state.suspendedStackTopFrame?.sourcePosition
      if (ctx != null && position != null && state.status is ChainStatus.Found) {
        StreamChainInlayState.Visible(ctx, position)
      } else {
        StreamChainInlayState.Hidden
      }
    }.distinctUntilChanged()

  // Both events are published with `syncPublisher`, and `processStopped` always comes strictly after `processStarted`,
  // so the state is created once per session and is always canceled.
  internal fun onProcessStarted(session: XDebugSession) {
    sessionStates.computeIfAbsent(session) { SessionState(it, cs.childScope("StreamDebugger session state")) }
  }

  internal fun onProcessStopped(session: XDebugSession) {
    sessionStates.remove(session)?.cancel()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private class SessionState(private val session: XDebugSession, private val scope: CoroutineScope) {
    /**
     * Fires on every event that can change the pause point or the frame selected in the UI.
     * `DROP_OLDEST` makes the producer non-blocking - `tryEmit` always succeeds.
     */
    private val sessionEvents = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
      val listener = object : XDebugSessionListener {
        override fun sessionPaused() { sessionEvents.tryEmit(Unit) }
        override fun stackFrameChanged() { sessionEvents.tryEmit(Unit) }
        override fun beforeSessionResume() { sessionEvents.tryEmit(Unit) }
        override fun sessionResumed() { sessionEvents.tryEmit(Unit) }
      }
      session.addSessionListener(listener, scope.asDisposable())
      sessionEvents.tryEmit(Unit)
    }

    /**
     * State of the pause point: the suspend context and the top frame of the suspended stack.
     * Frame selection does not change them, so [computeStatus] runs once per pause.
     */
    private val pausePointState: Flow<ChainDetectionState> = sessionEvents
      .map { session.suspendContext }
      .distinctUntilChanged()
      .map { suspendContext ->
        // `getTopFrame` is not a plain getter in every implementation (ex. Rider changes it when the frame filter settings change).
        // So read it once per pause, like `XDebugSessionImpl.updateSuspendContext` does.
        val topFrame = suspendContext?.activeExecutionStack?.topFrame
        ChainDetectionState(suspendContext, topFrame, ChainStatus.Computing)
      }
      .transformLatest { state ->
        val position = state.suspendedStackTopFrame?.sourcePosition
        if (state.suspendContext == null || position == null) {
          emit(state.copy(status = ChainStatus.NotFound))
          return@transformLatest
        }
        emit(state)
        emit(state.copy(status = computeStatus(position)))
      }

    // Current frame selected in the UI
    private val selectedFrame: Flow<XStackFrame?> = sessionEvents.map { session.currentStackFrame }.distinctUntilChanged()

    val status: StateFlow<ChainDetectionState> = combine(pausePointState, selectedFrame) { state, selectedFrame ->
      when {
        // The status belongs to the pause point. Report it only while the user has that frame selected.
        selectedFrame != null && selectedFrame == state.suspendedStackTopFrame -> state
        // Another frame or another thread: nothing to trace there.
        // Keep `LanguageNotSupported` explicitly so that the action stays
        // hidden where tracing never works, instead of showing it disabled.
        state.status is ChainStatus.LanguageNotSupported -> state
        else -> state.copy(status = ChainStatus.NotFound)
      }
    }.stateIn(scope, SharingStarted.WhileSubscribed(), ChainDetectionState(null, null, ChainStatus.Computing))

    fun cancel() {
      scope.cancel()
    }

    private suspend fun computeStatus(position: XSourcePosition): ChainStatus {
      val element = smartReadAction(session.project) { positionResolver.getNearestElementToBreakpoint(session.project, position) }
                    ?: return ChainStatus.NotFound
      val chains = smartReadAction(session.project) {
        if (providersFor(element.language).isEmpty()) null else getChains(element)
      } ?: return ChainStatus.LanguageNotSupported
      if (chains.isEmpty()) return ChainStatus.NotFound
      val traceable = filterTraceable(chains, position, element)
      return if (traceable.isEmpty()) ChainStatus.NotFound else ChainStatus.Found(traceable)
    }

    private suspend fun filterTraceable(
      chains: List<StreamChainWithLibrary>,
      position: XSourcePosition,
      contextElement: PsiElement,
    ): List<StreamChainWithLibrary> =
      chains.groupBy { it.provider }.flatMap { (provider, group) ->
        val traceable = provider.filterTraceableStreams(session, group.map { it.chain }, position, contextElement)
        group.filter { it.chain in traceable }
      }
  }

  companion object {
    fun getInstance(project: Project): ChainDetectionStateManager = project.service()
  }
}

@ApiStatus.Internal
data class ChainDetectionState(
  val suspendContext: XSuspendContext?,
  val suspendedStackTopFrame: XStackFrame?,
  val status: ChainStatus,
)

@ApiStatus.Internal
sealed interface StreamChainInlayState {
  data class Visible(val suspendContext: XSuspendContext, val position: XSourcePosition) : StreamChainInlayState
  object Hidden : StreamChainInlayState
}

private fun providersFor(language: Language): List<LibrarySupportProvider> =
  LibrarySupportProvider.EP_NAME.getByGroupingKey(language.id, ChainDetectionStateManager::class.java) { it.getLanguageId() }

private fun getChains(element: PsiElement): List<StreamChainWithLibrary> {
  val elementLanguageId = element.language.id
  val provider = LibrarySupportProvider.EP_NAME.findFirstSafe {
    it.getLanguageId() == elementLanguageId && it.getChainBuilder().isChainExists(element)
  } ?: return emptyList()
  return provider.getChainBuilder().build(element).map { StreamChainWithLibrary(it, provider) }.toList()
}
