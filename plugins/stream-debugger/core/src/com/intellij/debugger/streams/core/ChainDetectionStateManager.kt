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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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

  fun chainStateFlow(session: XDebugSession): Flow<ChainDetectionState> = sessionState(session)?.status ?: SESSION_FINISHED

  fun inlayStateFlow(session: XDebugSession): Flow<StreamChainInlayState> =
    chainStateFlow(session).mapNotNull { status ->
      val ctx = status.suspendContext ?: return@mapNotNull StreamChainInlayState.Hidden
      val topFrame = ctx.activeExecutionStack?.topFrame
      // check that frame is not changed
      if (topFrame != null && status.stackFrame != null && status.stackFrame !== topFrame) return@mapNotNull null
      val position = status.stackFrame?.sourcePosition
      if (status.status is ChainStatus.Found && position != null) {
        StreamChainInlayState.Visible(ctx, position)
      } else {
        StreamChainInlayState.Hidden
      }
    }.distinctUntilChanged()

  internal fun onProcessStarted(session: XDebugSession) {
    sessionState(session)
  }

  internal fun onProcessStopped(session: XDebugSession) {
    sessionStates.remove(session)?.cancel()
  }

  /**
   * Returns null for an already finished session
   */
  private fun sessionState(session: XDebugSession): SessionState? {
    if (session.isStopped) return null
    val state = sessionStates.computeIfAbsent(session) { SessionState(it, cs.childScope("StreamDebugger session state")) }
    if (session.isStopped) { // the session has finished concurrently with computeIfAbsent
      sessionStates.remove(session, state)
      state.cancel()
      return null
    }
    return state
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private class SessionState(private val session: XDebugSession, private val scope: CoroutineScope) {
    private fun snapshot(): ChainDetectionState =
      ChainDetectionState(session.suspendContext, session.currentStackFrame, ChainStatus.Computing)

    val status: StateFlow<ChainDetectionState> = channelFlow {
      val listener = object : XDebugSessionListener {
        override fun sessionPaused() {
          trySend(snapshot())
        }

        override fun stackFrameChanged() {
          trySend(snapshot())
        }

        override fun beforeSessionResume() {
          trySend(snapshot())
        }

        override fun sessionResumed() {
          trySend(snapshot())
        }
      }
      session.addSessionListener(listener, this.asDisposable())
      trySend(snapshot())
      awaitClose()
    }
      // `trySend` can silently drop the *newest* snapshot once the buffer is full.
      // With conflation `trySend` never fails, and a stale snapshot is dropped instead of the fresh one.
      .conflate()
      .distinctUntilChanged()
      .transformLatest { snap ->
        emit(snap)
        emit(snap.copy(status = computeStatus()))
      }
      .stateIn(scope, SharingStarted.WhileSubscribed(), ChainDetectionState(null, null, ChainStatus.Computing))

    fun cancel() {
      scope.cancel()
    }

    private suspend fun computeStatus(): ChainStatus {
      val element = smartReadAction(session.project) { positionResolver.getNearestElementToBreakpoint(session) }
                    ?: return ChainStatus.NotFound
      val chains = smartReadAction(session.project) {
        if (providersFor(element.language).isEmpty()) null else getChains(element)
      } ?: return ChainStatus.LanguageNotSupported
      if (chains.isEmpty()) return ChainStatus.NotFound
      val traceable = filterTraceable(chains, element)
      return if (traceable.isEmpty()) ChainStatus.NotFound else ChainStatus.Found(traceable)
    }

    private suspend fun filterTraceable(
      chains: List<StreamChainWithLibrary>,
      contextElement: PsiElement,
    ): List<StreamChainWithLibrary> =
      chains.groupBy { it.provider }.flatMap { (provider, group) ->
        val traceable = provider.filterTraceableStreams(session, group.map { it.chain }, contextElement)
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
  val stackFrame: XStackFrame?,
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
