// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.portForwarding

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.acceptOnTcpPort
import com.intellij.platform.eel.provider.utils.connectToTcpPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Default [TerminalPortForwardingManager] implementation that performs TCP port forwarding via [eelProxy].
 *
 * Forwardings are cached by [EelMachine] instead of [com.intellij.platform.eel.EelDescriptor] because descriptors can differ,
 * but point to the same [EelMachine].
 *
 * Forwardings live until [stopForwarding] is called or the application is shut down.
 *
 * ## Thread safety
 *
 * All operations can be called from any thread.
 *
 * Active forwardings are kept in an [AtomicReference] holding an immutable `List<PortForwardingEnvironment>`.
 * Reads and removals ([getForwardedLocalPort], [stopForwarding]) are lock-free — they consult and CAS-update the reference directly.
 *
 * The only operation that needs synchronization is starting a new proxy:
 * two parallel [forwardPort] calls for the same `(eelMachine, remotePort)` would otherwise race to bind two acceptors.
 * [startProxyMutex] guards that critical section.
 */
internal class TerminalPortForwardingManagerImpl(private val coroutineScope: CoroutineScope) : TerminalPortForwardingManager {
  /**
   * Snapshot of all active port forwardings, grouped by machine. The reference is replaced via CAS.
   * Each entry is immutable.
   */
  private val environments = AtomicReference<List<PortForwardingEnvironment>>(emptyList())

  /**
   * Held only across the proxy-startup section of [forwardPort]. Reads and stops do not take this
   * mutex — they operate on [environments] alone. The mutex prevents two concurrent [forwardPort]
   * calls from setting up duplicate proxies for the same `(eelMachine, remotePort)`.
   */
  private val startProxyMutex = Mutex()

  private val mutableStateChangedFlow = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )
  override val stateChangedFlow: Flow<Unit> = mutableStateChangedFlow.asSharedFlow()

  override fun getForwardedLocalPort(eelMachine: EelMachine, remotePort: Int): Int? {
    return environments.get()
      .firstOrNull { it.eelMachine == eelMachine }
      ?.forwardings
      ?.firstOrNull { it.remotePort == remotePort }
      ?.localPort
  }

  override suspend fun forwardPort(eelDescriptor: EelDescriptor, remotePort: Int): Int? {
    val eelMachine = eelDescriptor.resolveEelMachine()
    // Check if someone else may have already established this forwarding.
    getForwardedLocalPort(eelMachine, remotePort)?.let { return it }

    val remoteApi = eelDescriptor.toEelApi()

    return startProxyMutex.withLock {
      // Re-check inside the mutex: a parallel call may have completed while we were waiting.
      getForwardedLocalPort(eelMachine, remotePort)?.let { return@withLock it }

      val newForwarding = startProxy(remoteApi, remotePort, preferredLocal = remotePort)
                          ?: return@withLock null
      addForwarding(eelMachine, newForwarding)
      mutableStateChangedFlow.emit(Unit)
      LOG.debug { "Started forwarding from ${newForwarding.localPort} to remote ${newForwarding.remotePort} in ${eelMachine.internalName}" }
      newForwarding.localPort
    }
  }

  override fun stopForwarding(eelMachine: EelMachine, remotePort: Int) {
    val removed = removeForwarding(eelMachine, remotePort) ?: return
    removed.job.cancel()
    mutableStateChangedFlow.tryEmit(Unit)
    LOG.debug { "Stopping forwarding from ${removed.localPort} to remote ${removed.remotePort} in ${eelMachine.internalName}" }
  }

  /** CAS-updates [environments] to register [newForwarding] under [eelMachine]. */
  private fun addForwarding(eelMachine: EelMachine, newForwarding: PortForwarding) {
    environments.updateAndGet { current ->
      val envIndex = current.indexOfFirst { it.eelMachine == eelMachine }
      if (envIndex < 0) {
        current + PortForwardingEnvironment(eelMachine, listOf(newForwarding))
      }
      else {
        val env = current[envIndex]
        val newEnv = env.copy(forwardings = env.forwardings + newForwarding)
        current.toMutableList().also { it[envIndex] = newEnv }
      }
    }
  }

  /**
   * CAS-updates [environments] to drop the forwarding for `(eelMachine, remotePort)`.
   * Returns the removed [PortForwarding] (so the caller can cancel its job), or `null` if there
   * was nothing to remove.
   */
  private fun removeForwarding(eelMachine: EelMachine, remotePort: Int): PortForwarding? {
    val previous = environments.getAndUpdate { current ->
      val envIndex = current.indexOfFirst { it.eelMachine == eelMachine }
      if (envIndex < 0) return@getAndUpdate current
      val env = current[envIndex]
      val fwdIndex = env.forwardings.indexOfFirst { it.remotePort == remotePort }
      if (fwdIndex < 0) return@getAndUpdate current
      val remaining = env.forwardings.toMutableList().apply { removeAt(fwdIndex) }
      val updated = current.toMutableList()
      if (remaining.isEmpty()) {
        updated.removeAt(envIndex)
      }
      else {
        updated[envIndex] = env.copy(forwardings = remaining)
      }
      updated
    }
    return previous
      .firstOrNull { it.eelMachine == eelMachine }
      ?.forwardings
      ?.firstOrNull { it.remotePort == remotePort }
  }

  /**
   * Tries to bind the OS-preferred [preferredLocal] first.
   * On a connection error retries with `port = 0` so the OS picks any free local port.
   *
   * Caller MUST hold [startProxyMutex] when invoking this.
   */
  private suspend fun startProxy(remoteApi: EelApi, remotePort: Int, preferredLocal: Int?): PortForwarding? {
    if (preferredLocal != null && preferredLocal in 1..65535) {
      val attempt = tryStartProxy(remoteApi, remotePort, preferredLocal)
      if (attempt != null) return attempt
      LOG.debug { "Could not bind preferred local port $preferredLocal for remote port $remotePort in ${remoteApi.descriptor.name}, falling back to OS-picked" }
    }
    return tryStartProxy(remoteApi, remotePort, port = 0)
  }

  private suspend fun tryStartProxy(remoteApi: EelApi, remotePort: Int, port: Int): PortForwarding? {
    return try {
      val proxy = eelProxy()
        .acceptOnTcpPort(localEel.tunnels, host = "127.0.0.1", port = port.toUShort())
        .connectToTcpPort(remoteApi.tunnels, host = "127.0.0.1", port = remotePort.toUShort())
        .eelIt()
      val boundLocalPort = proxy.acceptor.boundAddress.port.toInt()
      val job = coroutineScope.launch { proxy.runForever() }
      PortForwarding(localPort = boundLocalPort, remotePort = remotePort, job = job)
    }
    catch (e: EelConnectionError) {
      if (port != 0) {
        // Caller will retry with port=0 — log at debug level only.
        LOG.debug(e) { "Failed to bind local port $port for remote port $remotePort in ${remoteApi.descriptor.name}" }
      }
      else {
        LOG.error("Failed to forward remote port $remotePort from ${remoteApi.descriptor.name}", e)
      }
      null
    }
  }

  companion object {
    private val LOG = logger<TerminalPortForwardingManagerImpl>()
  }
}

/**
 * One machine's worth of active forwardings.
 * Immutable. Replaced whole on every mutation of [TerminalPortForwardingManagerImpl.environments].
 */
private data class PortForwardingEnvironment(
  val eelMachine: EelMachine,
  val forwardings: List<PortForwarding>,
)

/**
 * One running tunnel. [job] runs the [eelProxy]'s `runForever` loop and is cancelled when the
 * forwarding is torn down.
 */
private data class PortForwarding(
  val localPort: Int,
  val remotePort: Int,
  val job: Job,
)