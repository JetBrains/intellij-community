// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.impl.TerminalViewImpl
import com.intellij.terminal.frontend.view.impl.awaitLaidOutTermSize
import com.intellij.terminal.frontend.view.portForwarding.installPortForwarding
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.fus.TerminalStartupFusInfo
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * A terminal process/session whose lifetime is independent of any one project presentation.
 *
 * The application scope is only the fallback lifetime parent. Callers own each returned handle and must close it.
 * Project-bound [TerminalView] instances can be replaced while the exact [TerminalSession] and process keep running.
 */
@ApiStatus.Internal
interface TransferableTerminalSession : AutoCloseable {
  val runtimeId: Long
  val coroutineScope: CoroutineScope
  val sessionState: StateFlow<TerminalViewSessionState>
  val view: TerminalView

  /** Replaces the project-specific presentation without restarting the process/session. Must be called on EDT. */
  fun bind(project: Project, sourceNavigationProjectPath: String? = null): TerminalView

  /** Creates and connects a destination presentation without replacing the current binding. */
  fun prepareBind(project: Project, sourceNavigationProjectPath: String? = null): PreparedTransferableTerminalBinding

  suspend fun processId(): Long

  override fun close()
}

@ApiStatus.Internal
interface PreparedTransferableTerminalBinding : AutoCloseable {
  val view: TerminalView

  /** Returns null when the destination owner was canceled before the atomic swap. */
  fun commit(): CommittedTransferableTerminalBinding?

  override fun close()
}

@ApiStatus.Internal
interface CommittedTransferableTerminalBinding {
  /** Completes the swap and releases the previous project binding. */
  fun finish()

  /** Restores the previous project binding and releases the prepared destination. */
  fun rollback()
}

/**
 * Owns an explicitly closed runtime scope and at most one replaceable project binding.
 *
 * Binding creation is intentionally performed outside the lifecycle monitor. The only lock order is the lifecycle
 * monitor by itself; callbacks and scope cancellation never run while it is held.
 */
@ApiStatus.Internal
class TransferableTerminalLifetime(parentScope: CoroutineScope) : AutoCloseable {
  class Binding<T : Any> internal constructor(
    val value: T,
    internal val scope: CoroutineScope,
    internal val ownerCompletion: kotlinx.coroutines.DisposableHandle,
  )

  class PreparedBinding<T : Any> internal constructor(
    private val lifetime: TransferableTerminalLifetime,
    internal val binding: Binding<T>,
  ) : AutoCloseable {
    private val claimed = AtomicBoolean(false)

    fun commit(): CommittedBinding<T>? {
      check(claimed.compareAndSet(false, true)) { "Prepared terminal binding was already completed" }
      return lifetime.commitPrepared(binding) ?: run {
        lifetime.disposeBinding(binding)
        null
      }
    }

    override fun close() {
      if (claimed.compareAndSet(false, true)) {
        lifetime.disposeBinding(binding)
      }
    }
  }

  class CommittedBinding<T : Any> internal constructor(
    private val lifetime: TransferableTerminalLifetime,
    internal val binding: Binding<T>,
    internal val previous: Binding<*>?,
  ) {
    private val completed = AtomicBoolean(false)

    fun finish() {
      if (completed.compareAndSet(false, true)) {
        lifetime.finishCommit(this)
      }
    }

    fun rollback() {
      if (completed.compareAndSet(false, true)) {
        lifetime.rollbackCommit(this)
      }
    }
  }

  val runtimeScope: CoroutineScope = parentScope.childScope("Transferable terminal runtime")

  private var currentBinding: Binding<*>? = null
  private var pendingCommit: CommittedBinding<*>? = null
  private var closed: Boolean = false

  fun <T : Any> prepareBinding(ownerScope: CoroutineScope, create: (CoroutineScope) -> T): PreparedBinding<T> {
    val bindingScope = runtimeScope.childScope("Transferable terminal presentation")
    val ownerCompletion = ownerScope.coroutineContext.job.invokeOnCompletion {
      bindingScope.cancel()
    }
    val value = try {
      create(bindingScope)
    }
    catch (t: Throwable) {
      ownerCompletion.dispose()
      bindingScope.cancel()
      throw t
    }
    val binding = Binding(value, bindingScope, ownerCompletion)
    if (isClosed() || !bindingScope.isActive) {
      disposeBinding(binding)
      error("Transferable terminal lifetime is already closed")
    }
    return PreparedBinding(this, binding)
  }

  fun <T : Any> replaceBinding(ownerScope: CoroutineScope, create: (CoroutineScope) -> T): Binding<T> {
    val prepared = prepareBinding(ownerScope, create)
    val committed = prepared.commit() ?: error("Transferable terminal binding owner is already canceled")
    committed.finish()
    return committed.binding
  }

  fun isClosed(): Boolean = synchronized(this) { closed }

  override fun close() {
    val bindings = synchronized(this) {
      if (closed) return
      closed = true
      val pendingPrevious = pendingCommit?.previous
      pendingCommit = null
      listOfNotNull(currentBinding, pendingPrevious).distinct().also { currentBinding = null }
    }
    bindings.forEach(::disposeBinding)
    runtimeScope.cancel()
  }

  private fun <T : Any> commitPrepared(binding: Binding<T>): CommittedBinding<T>? = synchronized(this) {
    if (closed || !binding.scope.isActive || pendingCommit != null) {
      return@synchronized null
    }
    val committed = CommittedBinding(this, binding, currentBinding)
    currentBinding = binding
    pendingCommit = committed
    committed
  }

  private fun finishCommit(commit: CommittedBinding<*>) {
    val previous = synchronized(this) {
      if (pendingCommit !== commit) return
      pendingCommit = null
      commit.previous
    }
    previous?.let(::disposeBinding)
  }

  private fun rollbackCommit(commit: CommittedBinding<*>) {
    val restorePrevious = synchronized(this) {
      if (pendingCommit !== commit) return
      pendingCommit = null
      if (!closed && currentBinding === commit.binding) {
        currentBinding = commit.previous
        true
      }
      else {
        false
      }
    }
    disposeBinding(commit.binding)
    if (!restorePrevious) {
      commit.previous?.let(::disposeBinding)
    }
  }

  private fun disposeBinding(binding: Binding<*>) {
    binding.ownerCompletion.dispose()
    binding.scope.cancel()
  }
}

@Service(Service.Level.PROJECT)
private class TransferableTerminalProjectBindingScope(val coroutineScope: CoroutineScope)

@ApiStatus.Internal
@Service(Service.Level.APP)
class TransferableTerminalSessionFactory(private val coroutineScope: CoroutineScope) {
  /**
   * @param deferSessionStartUntilUiShown when true, the returned session is bound but still
   * [TerminalViewSessionState.NotStarted]: the process is started once the bound view is shown, with the grid size
   * the view actually has, instead of the default [options] fall back to. Callers that must observe a running
   * process as soon as `create` returns keep the default; callers that show the view right away should not, because
   * a process started before layout paints its first frame at a guessed size.
   */
  suspend fun create(
    project: Project,
    options: ShellStartupOptions,
    sourceNavigationProjectPath: String? = null,
    startupFusInfo: TerminalStartupFusInfo? = null,
    deferSessionStartUntilUiShown: Boolean = false,
  ): TransferableTerminalSession {
    return TransferableTerminalSessionImpl.create(
      parentScope = coroutineScope,
      initialProject = project,
      requestedOptions = options,
      sourceNavigationProjectPath = sourceNavigationProjectPath,
      startupFusInfo = startupFusInfo,
      deferSessionStartUntilUiShown = deferSessionStartUntilUiShown,
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(): TransferableTerminalSessionFactory = service()
  }
}

@ApiStatus.Internal
@TestOnly
suspend fun createTransferableTerminalSessionForTest(
  parentScope: CoroutineScope,
  initialProject: Project,
  sessionStarter: (ShellStartupOptions, CoroutineScope) -> TerminalSession,
  stateTransitionObserver: (TerminalViewSessionState) -> Unit,
  deferSessionStartUntilUiShown: Boolean = false,
): TransferableTerminalSession {
  return TransferableTerminalSessionImpl.create(
    parentScope = parentScope,
    initialProject = initialProject,
    requestedOptions = ShellStartupOptions.Builder().build(),
    sourceNavigationProjectPath = null,
    startupFusInfo = null,
    deferSessionStartUntilUiShown = deferSessionStartUntilUiShown,
    sessionStarter = { _, options, scope -> sessionStarter(options, scope) },
    stateTransitionObserver = stateTransitionObserver,
  )
}

private class TransferableTerminalSessionImpl(
  parentScope: CoroutineScope,
  private val startupFusInfo: TerminalStartupFusInfo?,
  private val sessionStarter: (Project, ShellStartupOptions, CoroutineScope) -> TerminalSession,
  private val stateTransitionObserver: (TerminalViewSessionState) -> Unit = {},
) : TransferableTerminalSession {
  private val lifetime = TransferableTerminalLifetime(parentScope)
  override val coroutineScope: CoroutineScope
    get() = lifetime.runtimeScope

  override val runtimeId: Long = nextRuntimeId.getAndIncrement()

  private val mutableSessionState = MutableStateFlow<TerminalViewSessionState>(TerminalViewSessionState.NotStarted)
  override val sessionState: StateFlow<TerminalViewSessionState> = mutableSessionState.asStateFlow()
  private val closed = AtomicBoolean(false)
  private val stateLock = Any()
  private val viewLock = Any()

  /**
   * Completed once the process is running. A deferred start binds presentations before the session exists, so
   * every consumer of the session awaits it instead of reading a field that may not be initialized yet.
   */
  private val session = CompletableDeferred<TerminalSession>()

  @Volatile
  private lateinit var currentView: TerminalView

  override val view: TerminalView
    get() = currentView

  private suspend fun initialize(
    initialProject: Project,
    requestedOptions: ShellStartupOptions,
    sourceNavigationProjectPath: String?,
    deferSessionStartUntilUiShown: Boolean,
  ): TransferableTerminalSessionImpl {
    try {
      stateTransitionObserver(TerminalViewSessionState.NotStarted)
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        transitionToTerminated()
      }
      if (deferSessionStartUntilUiShown) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          bind(initialProject, sourceNavigationProjectPath)
          scheduleSessionStartOnShow(initialProject, requestedOptions, currentView)
        }
      }
      else {
        startSession(initialProject, requestedOptions)
        check(transitionToRunning()) { "Transferable terminal runtime terminated during startup" }
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          bind(initialProject, sourceNavigationProjectPath)
        }
        check(sessionState.value == TerminalViewSessionState.Running) { "Transferable terminal runtime terminated during startup" }
      }
      return this
    }
    catch (t: Throwable) {
      withContext(NonCancellable) {
        close()
      }
      throw t
    }
  }

  private suspend fun startSession(project: Project, options: ShellStartupOptions) {
    val started = withContext(Dispatchers.IO) {
      sessionStarter(project, options, coroutineScope)
    }
    session.complete(started)
  }

  /**
   * Starts the process once [view] is laid out, with the grid size it really has.
   *
   * The session stays [TerminalViewSessionState.NotStarted] until then, which is the state consumers already wait
   * to leave before they treat the runtime as usable. A failure here can no longer be thrown at the caller of
   * `create`, so it is reported through [sessionState] instead — the runtime closes and observers see `Terminated`.
   */
  private fun scheduleSessionStartOnShow(project: Project, options: ShellStartupOptions, view: TerminalView) {
    // Non-cancellable because the start must happen once even if the component is hidden again immediately.
    view.component.initOnShow(SESSION_START_NAME, context = NonCancellable) {
      // Launched on the runtime scope so the start survives the component being hidden again.
      coroutineScope.launch(CoroutineName(SESSION_START_NAME)) {
        try {
          startSession(project, options.builder().initialTermSize(view.awaitLaidOutTermSize()).build())
          transitionToRunning()
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.error("Failed to start the deferred transferable terminal session", t)
          withContext(NonCancellable) {
            close()
          }
        }
      }
    }
  }

  private fun transitionToRunning(): Boolean = synchronized(stateLock) {
    if (mutableSessionState.value != TerminalViewSessionState.NotStarted) return@synchronized false
    mutableSessionState.value = TerminalViewSessionState.Running
    stateTransitionObserver(TerminalViewSessionState.Running)
    true
  }

  private fun transitionToTerminated() = synchronized(stateLock) {
    if (mutableSessionState.value == TerminalViewSessionState.Terminated) return@synchronized
    mutableSessionState.value = TerminalViewSessionState.Terminated
    stateTransitionObserver(TerminalViewSessionState.Terminated)
  }

  override fun bind(project: Project, sourceNavigationProjectPath: String?): TerminalView {
    ThreadingAssertions.assertEventDispatchThread()
    val prepared = prepareBind(project, sourceNavigationProjectPath)
    val committed = prepared.commit() ?: run {
      prepared.close()
      error("Transferable terminal destination project is already disposed")
    }
    committed.finish()
    return prepared.view
  }

  override fun prepareBind(project: Project, sourceNavigationProjectPath: String?): PreparedTransferableTerminalBinding {
    ThreadingAssertions.assertEventDispatchThread()
    check(!closed.get()) { "Transferable terminal session is already closed" }
    val projectScope = project.service<TransferableTerminalProjectBindingScope>().coroutineScope
    val prepared = lifetime.prepareBinding(projectScope) { bindingScope ->
      TerminalViewImpl(
        project = project,
        settings = JBTerminalSystemSettingsProvider(),
        startupFusInfo = startupFusInfo,
        coroutineScope = bindingScope,
        sourceNavigationProjectPath = sourceNavigationProjectPath,
      )
    }
    val binding = prepared.binding
    binding.scope.launch {
      val activeSession = session.await()
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (binding.scope.isActive) {
          binding.value.connectToSession(activeSession)
          installPortForwarding(binding.value, binding.scope.childScope("PortForwarding"))
        }
      }
    }
    return object : PreparedTransferableTerminalBinding {
      override val view: TerminalView = binding.value
      private val completed = AtomicBoolean(false)

      override fun commit(): CommittedTransferableTerminalBinding? {
        check(completed.compareAndSet(false, true)) { "Prepared terminal binding was already completed" }
        val lifetimeCommit = prepared.commit() ?: return null
        val previousView = synchronized(viewLock) {
          val previous = if (this@TransferableTerminalSessionImpl::currentView.isInitialized) currentView else null
          currentView = view
          previous
        }
        return object : CommittedTransferableTerminalBinding {
          private val finished = AtomicBoolean(false)

          override fun finish() {
            if (finished.compareAndSet(false, true)) {
              lifetimeCommit.finish()
            }
          }

          override fun rollback() {
            if (!finished.compareAndSet(false, true)) return
            synchronized(viewLock) {
              if (currentView === view && previousView != null) {
                currentView = previousView
              }
            }
            lifetimeCommit.rollback()
          }
        }
      }

      override fun close() {
        if (completed.compareAndSet(false, true)) {
          prepared.close()
        }
      }
    }
  }

  override suspend fun processId(): Long = session.await().processId

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    transitionToTerminated()
    // Releases presentations that are still waiting for a session that will now never start.
    session.cancel()
    lifetime.close()
  }

  companion object {
    private val nextRuntimeId = AtomicLong(1)
    private const val SESSION_START_NAME = "Transferable terminal session start"

    suspend fun create(
      parentScope: CoroutineScope,
      initialProject: Project,
      requestedOptions: ShellStartupOptions,
      sourceNavigationProjectPath: String?,
      startupFusInfo: TerminalStartupFusInfo?,
      deferSessionStartUntilUiShown: Boolean = false,
      sessionStarter: (Project, ShellStartupOptions, CoroutineScope) -> TerminalSession = { project, options, scope ->
        startStandardTerminalSession(
          project = project,
          options = options,
          scope = scope,
          statisticsProject = null,
        ).session
      },
      stateTransitionObserver: (TerminalViewSessionState) -> Unit = {},
    ): TransferableTerminalSession {
      return TransferableTerminalSessionImpl(
        parentScope = parentScope,
        startupFusInfo = startupFusInfo,
        sessionStarter = sessionStarter,
        stateTransitionObserver = stateTransitionObserver,
      ).initialize(
        initialProject = initialProject,
        requestedOptions = requestedOptions,
        sourceNavigationProjectPath = sourceNavigationProjectPath,
        deferSessionStartUntilUiShown = deferSessionStartUntilUiShown,
      )
    }
  }
}

private val LOG = logger<TransferableTerminalSession>()
