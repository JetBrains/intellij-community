// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.acceptOnTcpPort
import com.intellij.platform.eel.provider.utils.connectToTcpPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.ide.BuiltInServerManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<McpWslForwarderService>()

/**
 * Reconciles per-WSL-distro TCP forwarders that expose the IDE's MCP SSE server and the built-in HTTP server
 * on loopback inside each attached WSL distro, so tools running in WSL (e.g. Claude Code) can reach them
 * without a `0.0.0.0` bind on the Windows side.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class McpWslForwarderService(private val cs: CoroutineScope) {

  /** Which IDE-side loopback port the forwarder is proxying into the distro. */
  enum class PortKind { MCP, BUILT_IN_HTTP }

  private data class ForwarderKey(val distro: WSLDistribution, val kind: PortKind)

  /**
   * Live forwarder jobs, one per `(distro, kind)` pair. Presence in the map ⇔ the forwarder is expected to
   * be running. Reads and writes are guarded by [distroMutex] for the corresponding distro.
   */
  private val jobs = ConcurrentHashMap<ForwarderKey, Job>()

  /** Per-distro mutex map, lazily populated. Never removed for the lifetime of the service. */
  private val distroMutex = ConcurrentHashMap<WSLDistribution, Mutex>()

  /**
   * Best-effort snapshot of currently attached distros. Populated from the stopgap
   * [WslDistributionManager] change listener; consulted by [readDistroAttachedSignal].
   */
  private val attachedDistros = CopyOnWriteArraySet<WSLDistribution>()

  /**
   * Single reconcile-trigger surface. Callers ([requestReconcile]) emit a distro; the collector below
   * turns each emission into a debounced call to [reconcileLocked].
   *
   * `replay = 0` — reconciliation state is not something a late subscriber should replay;
   * `extraBufferCapacity = 64` — absorbs bursts (e.g. several distros attaching at once) without
   * suspending the caller.
   */
  private val reconcileRequests: MutableSharedFlow<WSLDistribution> =
    MutableSharedFlow(replay = 0, extraBufferCapacity = 64)

  /**
   * Per-distro debounce jobs. Each new emission cancels the pending timer for that distro and starts a
   * fresh 200 ms wait before actually calling [reconcileLocked].
   */
  private val debounceJobs = ConcurrentHashMap<WSLDistribution, Job>()

  init {
    // In unit-test mode, mirror BuiltInServerManagerImpl's short-circuit: do not start collectors or
    // touch WSL from tests that merely instantiate the service graph.
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      startReconcileCollector()
      subscribeToMcpServerFlow()
      subscribeToBuiltInHttpStart()
      subscribeToWslDistroChanges()
    }
  }

  private fun startReconcileCollector() {
    cs.launch(CoroutineName("mcp-wsl-forwarder-collector")) {
      reconcileRequests.collect { distro ->
        scheduleReconcile(distro)
      }
    }
  }

  /**
   * Subscribe to [McpServerService.serverPortFlow]; every start/stop transition reconciles every known
   * distro. This replaces the previous polling of `isRunning` / `port`.
   */
  private fun subscribeToMcpServerFlow() {
    val svc = serviceOrNull<McpServerService>() ?: return
    cs.launch(CoroutineName("mcp-wsl-forwarder-mcp-signal")) {
      svc.serverPortFlow.collect { port ->
        LOG.debug { "MCP server port signal → $port" }
        reconcileAllKnownDistros("MCP server port changed to $port")
      }
    }
  }

  /**
   * Wait for the built-in HTTP server's initial bind, then reconcile all known distros. This is a stopgap
   * for the missing `BuiltInServerListener`; once that lands, we should
   * also react to server-stopped events.
   */
  private fun subscribeToBuiltInHttpStart() {
    cs.launch(CoroutineName("mcp-wsl-forwarder-builtin-http-start")) {
      try {
        // waitForStart() is a blocking call that asserts non-EDT — run it on Dispatchers.IO.
        withContext(Dispatchers.IO) { BuiltInServerManager.getInstance().waitForStart() }
      }
      catch (t: Throwable) {
        LOG.debug("Built-in HTTP server did not start", t)
        return@launch
      }
      reconcileAllKnownDistros("built-in HTTP server started on port ${BuiltInServerManager.getInstance().port}")
    }
  }

  /**
   * Populate [attachedDistros] from the current WSL enumeration and subscribe to
   * [WslDistributionManager.addWslDistributionsChangeListener] for future changes.
   */
  private fun subscribeToWslDistroChanges() {
    val mgr = try {
      WslDistributionManager.getInstance()
    }
    catch (t: Throwable) {
      LOG.debug("WSL distribution manager unavailable", t)
      return
    }
    cs.launch(CoroutineName("mcp-wsl-forwarder-initial-distros")) {
      val initial = try {
        mgr.installedDistributionsFuture.await()
      }
      catch (t: Throwable) {
        LOG.debug("Failed to enumerate installed WSL distributions", t)
        return@launch
      }
      for (d in initial) {
        attachedDistros.add(d)
        requestReconcile(d)
      }
    }
    mgr.addWslDistributionsChangeListener { before, after ->
      val added = after - before
      val removed = before - after
      for (d in added) {
        attachedDistros.add(d)
        LOG.info("WSL distro attached: ${d.msId}")
        requestReconcile(d)
      }
      for (d in removed) {
        attachedDistros.remove(d)
        LOG.info("WSL distro detached: ${d.msId}")
        requestReconcile(d)
      }
    }
  }

  private fun reconcileAllKnownDistros(reason: String) {
    val snapshot = attachedDistros.toList()
    if (snapshot.isEmpty()) {
      LOG.debug { "Reconcile trigger '$reason' but no distros are known yet" }
      return
    }
    LOG.debug { "Reconcile trigger '$reason' → ${snapshot.size} distro(s)" }
    for (d in snapshot) requestReconcile(d)
  }

  /**
   * Public entry point for the three signal sources (MCP server, built-in HTTP server, WSL attach tracker).
   * Non-blocking; safe to call from any thread and any dispatcher, including EDT.
   *
   * Callers pass the distro whose state might have changed; when a signal is IDE-global (MCP or built-in
   * HTTP started/stopped), the caller should invoke [requestReconcile] once per currently-known distro
   * (see [reconcileAllKnownDistros]).
   */
  fun requestReconcile(distro: WSLDistribution) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    val emitted = reconcileRequests.tryEmit(distro)
    if (!emitted) {
      // With extraBufferCapacity=64 and replay=0 this should be effectively unreachable; log so we notice
      // if the invariant ever changes.
      LOG.warn("Dropped reconcile request for ${distro.msId}: SharedFlow buffer full")
    }
  }

  private fun scheduleReconcile(distro: WSLDistribution) {
    // Cancel any pending debounce for this distro and schedule a fresh one.
    debounceJobs[distro]?.cancel()
    debounceJobs[distro] = cs.launch(CoroutineName("mcp-wsl-forwarder-debounce/${distro.msId}")) {
      try {
        delay(RECONCILE_DEBOUNCE)
        reconcileLocked(distro)
      }
      finally {
        // Only clear our own slot; a newer job may have already replaced us.
        debounceJobs.remove(distro, coroutineContextJob())
      }
    }
  }

  private suspend fun reconcileLocked(distro: WSLDistribution) {
    val mutex = distroMutex.computeIfAbsent(distro) { Mutex() }
    mutex.withLock {
      val enabled = isFeatureEnabled()
      val s1McpPort = if (enabled) readMcpSignal() else null
      val s2HttpPort = if (enabled) readBuiltInHttpSignal() else null
      val s3Attached = enabled && readDistroAttachedSignal(distro)

      for (kind in PortKind.entries) {
        val key = ForwarderKey(distro, kind)
        val idePort: Int? = when (kind) {
          PortKind.MCP -> s1McpPort
          PortKind.BUILT_IN_HTTP -> s2HttpPort
        }
        val shouldRun = s3Attached && idePort != null
        val existing = jobs[key]
        when {
          shouldRun && existing == null -> {
            val job = cs.launch(
              SupervisorJob(cs.coroutineContextJob()) +
              CoroutineName("mcp-wsl-fwd/${distro.msId}/$kind")
            ) {
              runOne(distro, kind, requireNotNull(idePort))
            }
            jobs[key] = job
            LOG.info("Started MCP<->WSL forwarder: distro=${distro.msId} kind=$kind idePort=$idePort")
          }
          !shouldRun && existing != null -> {
            jobs.remove(key)
            existing.cancelAndJoin()
            LOG.info("Stopped MCP<->WSL forwarder: distro=${distro.msId} kind=$kind")
          }
        }
      }
    }
  }

  /**
   * One-forwarder body for a single `(distro, kind)` pair against a single running WSL distribution.
   */
  private suspend fun runOne(distro: WSLDistribution, kind: PortKind, idePort: Int) {
    require(idePort in 1..0xFFFF) { "invalid IDE-side port $idePort for $kind" }

    val debugLabel = "mcp-wsl-fwd/${distro.msId}/$kind[ide=$idePort]"

    // Resolve the remote EelApi for this WSL distribution via public API only:
    //   WSLDistribution -> UNC root path -> EelDescriptor -> EelApi
    // In mirrored networking mode the resolved EelApi's tunnels transparently delegate to
    // localEel.tunnels (see WslEelMachine.toEelApi), and eelProxy() short-circuits accept+connect on
    // the same EelTunnelsApi via _fakeProxyPossible — so this path is a no-op there.
    val descriptor = distro.getUNCRootPath().getEelDescriptor()
    if (descriptor === LocalEelDescriptor) {
      LOG.warn("$debugLabel: could not resolve WSL EelDescriptor for '${distro.msId}'; " +
               "eel-nio filesystem backend is not registered")
      return
    }
    val remoteEel = descriptor.toEelApi()

    // Prefer binding on the same port inside the distro so clients don't have to discover a new one.
    val preferredPort = idePort.toUShort()

    val proxy = try {
      eelProxy()
        .acceptOnTcpPort(remoteEel.tunnels, port = preferredPort)
        .connectToTcpPort(localEel.tunnels, port = idePort.toUShort())
        .debugLabel(debugLabel)
        .eelIt()
    }
    catch (e: EelConnectionError) {
      LOG.info("$debugLabel: could not bind in-distro port $preferredPort ($e); " +
               "retrying with an ephemeral port")
      // TODO: expose the ephemeral-port mapping to the discovery layer so
      //  WSL clients can find the actual forwarded port.
      eelProxy()
        .acceptOnTcpPort(remoteEel.tunnels, port = 0u)
        .connectToTcpPort(localEel.tunnels, port = idePort.toUShort())
        .debugLabel(debugLabel)
        .eelIt()
    }

    val boundPort = proxy.acceptor.boundAddress.port
    LOG.info("$debugLabel: acceptor bound inside distro on 127.0.0.1:$boundPort → localhost:$idePort")

    try {
      proxy.runForever()
    }
    catch (e: CancellationException) {
      // Normal path: reconcileLocked() cancels this coroutine when any of the three signals flips
      // to false, or when the service scope is cancelled on IDE shutdown.
      throw e
    }
    catch (e: Throwable) {
      // Let the SupervisorJob confine the failure to this (distro, kind) child; reconcileLocked
      // will re-evaluate the signals when they next transition and relaunch if still applicable.
      LOG.warn("$debugLabel: forwarder terminated with an error", e)
      throw e
    }
    finally {
      LOG.info("$debugLabel: acceptor on 127.0.0.1:$boundPort closed")
    }
  }

  // ---------------------------------------------------------------------------
  // Signal accessors.
  // ---------------------------------------------------------------------------

  private fun isFeatureEnabled(): Boolean =
    Registry.`is`(FORWARDER_REGISTRY_KEY, false)

  /**
   * @return the IDE-side loopback port on which the MCP SSE server is listening, or `null` if the server is
   *   not running.
   */
  private fun readMcpSignal(): Int? {
    val svc = serviceOrNull<McpServerService>() ?: return null
    return svc.serverPortFlow.value
  }

  /**
   * @return the IDE-side loopback port on which the built-in HTTP server is listening, or `null` if it is
   *   not started.
   */
  private fun readBuiltInHttpSignal(): Int? {
    // TODO: subscribe to a BuiltInServerListener MessageBus Topic instead of polling
    //  BuiltInServerManager.port so 'server stopped' events also propagate.
    val port = BuiltInServerManager.getInstance().port
    return if (port > 0) port else null
  }

  /**
   * @return `true` while [distro] is attached (present in the last observed WSL enumeration).
   */
  private fun readDistroAttachedSignal(distro: WSLDistribution): Boolean {
    // TODO: subscribe to WslDistroAttachTracker events tied to the IJent session
    //  lifecycle (survives `wsl --shutdown`); the WslDistributionManager change listener tracks
    //  *installed* distros, not per-session attach/detach.
    return distro in attachedDistros
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private suspend fun coroutineContextJob(): Job =
    kotlin.coroutines.coroutineContext[Job] ?: error("no Job in coroutine context")

  private fun CoroutineScope.coroutineContextJob(): Job =
    this.coroutineContext[Job] ?: error("no Job in coroutine context")

  companion object {
    /**
     * Prototype gate for the WSL forwarder. Default `false` — must stay off in
     * released builds until the design has soaked.
     */
    const val FORWARDER_REGISTRY_KEY: String = "ide.mcp.wsl.forward.enabled"

    /** Debounce window applied per-distro to coalesce reconcile requests. */
    private val RECONCILE_DEBOUNCE = 200.milliseconds

    @JvmStatic
    fun getInstance(): McpWslForwarderService = service()
  }

  /**
   * Touches [McpWslForwarderService] on the first project open so that its `init { }` block runs and the
   * three signal subscriptions come up. The service itself is registered as `os="windows"` in the plugin
   * XML, but we defensively re-check [SystemInfo.isWindows] here too.
   */
  internal class StartupTouch : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (!SystemInfo.isWindows) return
      serviceOrNull<McpWslForwarderService>()
    }
  }
}
