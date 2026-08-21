// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelProxy
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.acceptOnTcpPort
import com.intellij.platform.eel.provider.utils.asInetAddress
import com.intellij.platform.eel.provider.utils.connectToTcpPort
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectIdOrNull
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.ide.BuiltInServerManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<McpWslForwarderService>()

/**
 * Reconciles per-WSL-distro TCP forwarders that expose the IDE's MCP SSE server and the built-in HTTP server
 * on loopback inside a WSL distro, so tools running in WSL (e.g. Claude Code) can reach them without a
 * `0.0.0.0` bind on the Windows side.
 *
 * Forwarders are scoped to the open-project lifecycle: a distro is forwarded into while at least one open
 * project lives on it, and the forwarders are torn down when the last such project closes -- even if the
 * distro itself keeps running. Distros without an open project are never touched at all; in particular no
 * [EelApi] is resolved for them, so an installed-but-stopped distro is not booted just because the IDE runs.
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
   * The WSL distribution hosting each currently open project, for the projects that live on one. A distro is
   * present as a value <=> it has at least one open project <=> it should be forwarded into. Maintained by
   * [onProjectOpened] / [onProjectClosed] and consulted by [readDistroInUseSignal].
   *
   * Keyed by the string identity from [projectKeyOf].
   */
  private val distroByProjectId = ConcurrentHashMap<String, WSLDistribution>()

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
    //
    // Also stay completely off when the registry flag is not explicitly set — no coroutines
    // launched in [cs], no subscription to McpServerService.serverPortFlow.
    //
    if (!ApplicationManager.getApplication().isUnitTestMode && isFeatureEnabled()) {
      startReconcileCollector()
      subscribeToMcpServerFlow()
      subscribeToBuiltInHttpStart()
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
   * Marks the distro hosting [project] as in use and reconciles it, which starts the forwarders once the MCP
   * and built-in HTTP signals are up too. Projects that do not live on a WSL distro are ignored, and so is a
   * second call for a project that is already registered.
   *
   * The close edge comes from [CloseHook], a project-level service materialized here, rather than from an
   * application-level `ProjectCloseListener`: the entry then cannot outlive the project it describes, and
   * nothing is subscribed for projects this service never cared about.
   */
  fun onProjectOpened(project: Project) {
    if (!isFeatureEnabled()) return
    val distro = distroOf(project) ?: return
    val projectKey = projectKeyOf(project)
    val projectName = project.name
    // Materialize the close hook before claiming the distro. On an already-disposed project this throws
    // AlreadyDisposedException (a cancellation exception) before anything was claimed, which is the correct
    // outcome: no entry, no forwarder.
    project.service<CloseHook>()
    if (distroByProjectId.putIfAbsent(projectKey, distro) == null) {
      LOG.info("Project '$projectName' opened on WSL distro '${distro.msId}'")
      if (project.isDisposed) {
        // Disposal started between the two statements above, so [CloseHook] may have already run and missed
        // the entry. `isDisposed` flips before children are disposed, so the converse cannot happen.
        onProjectClosed(projectKey, projectName)
        return
      }
    }
    requestReconcile(distro)
  }

  /**
   * Drops the project identified by [projectKey] from the in-use set and reconciles its distro, which stops
   * the forwarders when this was the last open project on that distro. That the distro itself may still be
   * running is irrelevant: with no project left there is nothing in the IDE for a WSL-side client to talk to.
   */
  internal fun onProjectClosed(projectKey: String, projectName: String) {
    val distro = distroByProjectId.remove(projectKey) ?: return
    LOG.info("Project '$projectName' on WSL distro '${distro.msId}' closed")
    // Reconcile the distro even though it may have just left the map -- that is exactly the transition that
    // has to stop the forwarder.
    requestReconcile(distro)
  }

  /**
   * @return the WSL distribution hosting [project], or `null` if the project does not live inside one.
   *
   * Path-based on purpose: [WslPath] only parses the WSL UNC prefix of the project path and maps the distro
   * id onto a [WSLDistribution] handle. Neither step starts the distro or resolves an [EelApi], which is
   * what keeps a stopped distro stopped until a project on it is actually opened.
   */
  private fun distroOf(project: Project): WSLDistribution? {
    val basePath = project.guessProjectDir()?.path ?: return null
    return WslPath.getDistributionByWindowsUncPath(basePath)
  }

  private fun reconcileAllKnownDistros(reason: String) {
    val snapshot = distroByProjectId.values.distinct()
    if (snapshot.isEmpty()) {
      LOG.debug { "Reconcile trigger '$reason' but no open project lives on a WSL distro" }
      return
    }
    LOG.debug { "Reconcile trigger '$reason' → ${snapshot.size} distro(s)" }
    for (d in snapshot) requestReconcile(d)
  }

  /**
   * Public entry point for the three signal sources (MCP server, built-in HTTP server, project lifecycle).
   * Non-blocking; safe to call from any thread and any dispatcher, including EDT.
   *
   * Callers pass the distro whose state might have changed; when a signal is IDE-global (MCP or built-in
   * HTTP started/stopped), the caller should invoke [requestReconcile] once per distro that currently hosts
   * an open project (see [reconcileAllKnownDistros]).
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
      val s3InUse = enabled && readDistroInUseSignal(distro)

      for (kind in PortKind.entries) {
        val key = ForwarderKey(distro, kind)
        val idePort: Int? = when (kind) {
          PortKind.MCP -> s1McpPort
          PortKind.BUILT_IN_HTTP -> s2HttpPort
        }
        val shouldRun = s3InUse && idePort != null
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

    // WSL1 distros share the Windows network stack: "loopback inside the distro" is Windows loopback, so
    // forwarding buys nothing and the per-distro isolation this feature assumes does not exist there.
    //
    if (withContext(Dispatchers.IO) { distro.version } == 1) {
      LOG.info("$debugLabel: skipping WSL1 distro '${distro.msId}' — it shares the Windows network stack")
      return
    }

    // Resolve the remote EelApi for this WSL distribution via public API only:
    //   WSLDistribution -> UNC root path -> EelDescriptor -> EelApi
    //
    // TODO: skip mirrored networking mode explicitly (IJPL-232933, security review finding F3).
    //  In mirrored mode `WslEelMachine.toEelApi` swaps in a tunnels implementation that delegates *both*
    //  accept and connect to localEel.tunnels, and eelProxy()'s `_fakeProxyPossible` short-circuit does not
    //  fire for the reverse (accept-remote / connect-local) direction because the acceptor's descriptor is
    //  not LocalEelDescriptor. The result is a redundant Windows-side loopback listener, not the intended
    //  no-op. Detection lives in the platform (`isMirroredMode`) but is `internal` today.
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
      acceptInDistro(remoteEel, bindPort = preferredPort, idePort = idePort, debugLabel = debugLabel)
    }
    catch (e: EelConnectionError) {
      LOG.info("$debugLabel: could not bind in-distro port $preferredPort ($e); " +
               "retrying with an ephemeral port")
      // TODO: expose the ephemeral-port mapping to the discovery layer so
      //  WSL clients can find the actual forwarded port.
      acceptInDistro(remoteEel, bindPort = 0u, idePort = idePort, debugLabel = debugLabel)
    }

    // Security invariant (IJPL-200926): the endpoint must stay loopback-only. `hostname` in the accept
    // request is resolved *inside* the distro, so a broken or hostile /etc/hosts (or a future Eel change)
    // could still land the acceptor on eth0 — an address that is reachable from the Windows host and from
    // every other running WSL2 distro, and that is LAN-routable under `networkingMode=bridged`. Verify the
    // address we actually got instead of trusting the request, and refuse to serve on anything else.
    val boundAddress = proxy.acceptor.boundAddress.asInetAddress()
    if (!boundAddress.address.isLoopbackAddress) {
      LOG.warn("$debugLabel: refusing to expose the IDE endpoint — acceptor bound to non-loopback " +
               "address $boundAddress inside '${distro.msId}'")
      proxy.acceptor.close()
      return
    }
    LOG.info("$debugLabel: acceptor bound inside distro on $boundAddress → localhost:$idePort")

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
      LOG.info("$debugLabel: acceptor on $boundAddress closed")
    }
  }

  /**
   * Opens the in-distro acceptor and wires it to the IDE-side loopback port.
   *
   * The accept side is pinned to the literal [LOOPBACK_V4] rather than the `localhost` default of
   * [acceptOnTcpPort]: the hostname is resolved by the distro, and this is the only bind in the whole path
   * that faces a network the IDE does not control.
   *
   * @param bindPort port to bind inside the distro, or `0` to let the distro pick an ephemeral one.
   */
  @ThrowsChecked(EelConnectionError::class)
  private suspend fun acceptInDistro(remoteEel: EelApi, bindPort: UShort, idePort: Int, debugLabel: String): EelProxy =
    eelProxy()
      .acceptOnTcpPort(remoteEel.tunnels, host = LOOPBACK_V4, port = bindPort)
      .connectToTcpPort(localEel.tunnels, port = idePort.toUShort())
      .debugLabel(debugLabel)
      .eelIt()

  // ---------------------------------------------------------------------------
  // Signal accessors.
  // ---------------------------------------------------------------------------

  @OptIn(LowLevelLocalMachineAccess::class)
  private fun isFeatureEnabled(): Boolean =
    Registry.`is`(FORWARDER_REGISTRY_KEY, false) && OS.CURRENT == OS.Windows

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
   * @return `true` while at least one open project lives on [distro].
   *
   * Deliberately *not* "is the distro attached": forwarding exists to serve open projects, and enumerating
   * attached distros would mean resolving an [EelApi] per installed distro, which boots them.
   */
  private fun readDistroInUseSignal(distro: WSLDistribution): Boolean = distroByProjectId.containsValue(distro)

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun CoroutineScope.coroutineContextJob(): Job =
    this.coroutineContext[Job] ?: error("no Job in coroutine context")

  companion object {
    /**
     * Prototype gate for the WSL forwarder. Default `false` — must stay off in
     * released builds until the design has soaked.
     */
    const val FORWARDER_REGISTRY_KEY: String = "ide.mcp.wsl.forward.enabled"

    /**
     * The only address the in-distro acceptor is ever allowed to bind. A literal is used on purpose: the
     * `localhost` default of [acceptOnTcpPort] is resolved inside the distro and is therefore attacker- and
     * misconfiguration-controlled (on a stock Ubuntu-22.04 distro `getent hosts localhost` already answers
     * `::1`, not `127.0.0.1`).
     */
    private const val LOOPBACK_V4: String = "127.0.0.1"

    /** Debounce window applied per-distro to coalesce reconcile requests. */
    private val RECONCILE_DEBOUNCE = 200.milliseconds

    @JvmStatic
    fun getInstance(): McpWslForwarderService = service()

    /**
     * @return a stable string identity for [project], used as the key of [distroByProjectId].
     *
     * [ProjectId] is the platform's own instance identity and is exactly what a long-lived map should hold in
     * place of the [Project] itself. It is unregistered when the project is disposed, so callers must read it
     * while the project is still alive and keep the resulting string. [Project.getLocationHash] is the
     * fallback for project implementations that never registered an id; it is unique among simultaneously
     * open projects, which is all this map needs.
     */
    private fun projectKeyOf(project: Project): String =
      project.projectIdOrNull()?.serializeToString() ?: project.locationHash
  }

  /**
   * Instantiates [McpWslForwarderService] on project open -- which brings up the `init { }` subscriptions --
   * and reports the newly opened project, so that a forwarder is started for the distro hosting it, if any.
   *
   * The service itself is registered as `os="windows"` in the plugin XML, but we defensively re-check
   * [SystemInfo.isWindows] here too.
   */
  internal class ProjectOpenTracker : ProjectActivity {
    override suspend fun execute(project: Project) {
      if (!SystemInfo.isWindows) return
      serviceOrNull<McpWslForwarderService>()?.onProjectOpened(project)
    }
  }

  /**
   * Delivers the "project closed" edge to [onProjectClosed]. A project-level service is disposed when its
   * project closes and is the sanctioned parent for project-scoped state in plugin code -- registering a
   * [Disposable] on the [Project] instance itself is not. It is created on demand from [onProjectOpened],
   * i.e. only for projects that actually live on a WSL distro.
   */
  @Service(Service.Level.PROJECT)
  internal class CloseHook(project: Project) : Disposable {
    /**
     * Captured eagerly, i.e. while the project is still open: [dispose] runs during teardown, by which time
     * the platform may already have unregistered the project's [ProjectId]. Keeping the two strings also
     * stops this hook from handing a half-disposed [Project] back to the application-level service.
     */
    private val projectKey: String = projectKeyOf(project)
    private val projectName: String = project.name

    override fun dispose() {
      // On IDE shutdown the application container may already be gone; there is nothing to reconcile then.
      serviceIfCreated<McpWslForwarderService>()?.onProjectClosed(projectKey, projectName)
    }
  }
}
