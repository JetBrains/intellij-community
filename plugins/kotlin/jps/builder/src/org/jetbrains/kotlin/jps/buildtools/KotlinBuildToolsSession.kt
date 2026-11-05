// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.jetbrains.kotlin.buildtools.api.ExecutionPolicy
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.daemonExecutionPolicy
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The [KotlinToolchains.BuildSession] of one JPS build.
 *
 * The session is created on the first Kotlin chunk, so that a build without Kotlin never loads the implementation.
 * `KotlinBuilder.buildFinished` closes it.
 *
 * The session also owns the execution policy of the build. The implementation keeps one daemon connection per policy
 * instance for the whole session, so the policy is created once per build, not per compilation. The daemon is selected
 * by the `kotlin.daemon.enabled` system property, as in the legacy Kotlin JPS plugin.
 */
class KotlinBuildToolsSession internal constructor() {
  private var session: KotlinToolchains.BuildSession? = null
  private var executionPolicy: ExecutionPolicy? = null
  private var daemonPolicyReported = false

  /**
   * @return the session, or `null` when the IDE handed over no implementation.
   */
  @Synchronized
  fun getOrCreate(): KotlinToolchains.BuildSession? {
    session?.let { return it }
    val toolchains = KotlinBuildToolsLoader.load() ?: return null
    return toolchains.createBuildSession().also {
      session = it
      executionPolicy =
        if (CompilerSystemProperties.COMPILE_DAEMON_ENABLED_PROPERTY.value != null) {
          toolchains.daemonExecutionPolicy {
            this[ExecutionPolicy.WithDaemon.SHUTDOWN_DELAY_MILLIS] = DAEMON_SHUTDOWN_DELAY.inWholeMilliseconds
          }
        }
        else {
          toolchains.createInProcessExecutionPolicy()
        }
    }
  }

  /**
   * @return the execution policy of the build: the daemon policy while `kotlin.daemon.enabled` is set.
   *
   * @throws IllegalStateException when the session does not exist.
   */
  @Synchronized
  fun executionPolicy(): ExecutionPolicy = executionPolicy ?: error("The Build Tools API session does not exist")

  /**
   * Replaces the policy with an in-process one, for the rest of the build. The implementation keeps no failed daemon
   * connection, so the daemon policy would retry an expensive daemon start on every target.
   *
   * @return the in-process policy.
   *
   * @throws IllegalStateException when the session does not exist.
   */
  @Synchronized
  fun fallBackToInProcess(): ExecutionPolicy {
    val toolchains = session?.kotlinToolchains ?: error("The Build Tools API session does not exist")
    return toolchains.createInProcessExecutionPolicy().also { executionPolicy = it }
  }

  /**
   * Reports the daemon policy once per build, so that a per-chunk report does not repeat it.
   *
   * @return `true` once while the daemon policy is active.
   */
  @Synchronized
  fun consumeDaemonPolicyReport(): Boolean {
    if (executionPolicy !is ExecutionPolicy.WithDaemon || daemonPolicyReported) return false
    daemonPolicyReported = true
    return true
  }

  @Synchronized
  fun close() {
    session?.close()
    session = null
    executionPolicy = null
  }

  private companion object {
    /**
     * The time that the daemon lives after its last client exits. The build process is a client, and it exits after each
     * build. The `kotlin.daemon.options=shutdownDelayMilliseconds=<ms>` system property replaces the delay.
     */
    val DAEMON_SHUTDOWN_DELAY: Duration = 30.minutes
  }
}
