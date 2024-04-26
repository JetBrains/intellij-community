// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The status of the debugger looking from IDE side.
 *
 * @see ClientModeMultiProcessDebugger
 */
private enum class ClientModeDebuggerStatus {
  /**
   * The status before trying to connect to the main process.
   *
   * @see ClientModeMultiProcessDebugger.waitForConnect
   */
  INITIAL,

  /**
   * The status after requesting to establish the connection with the main
   * process but before the connection succeeded or failed.
   *
   * @see ClientModeMultiProcessDebugger.waitForConnect
   */
  CONNECTING,

  /**
   * Indicates that this [ClientModeMultiProcessDebugger] has connected
   * at least to one (the main one) Python debugging process.
   *
   * *As Python debugging process does not start the underlying Python
   * script before we send him [RunCommand] so the first process we
   * connected to is the main Python script process.*
   */
  CONNECTED,

  /**
   * Corresponds to one of the two alternatives:
   *  - the debugger script process is terminated;
   *  - the user requested to detach IDE from the debugger script.
   */
  DISCONNECTION_INITIATED
}

internal class ClientModeDebuggerStatusHolder {
  private val lock: ReentrantLock = ReentrantLock()

  private val condition: Condition = lock.newCondition()

  /**
   * Guarded by [lock].
   */
  private var status: ClientModeDebuggerStatus = ClientModeDebuggerStatus.INITIAL

  /**
   * Switches the current status from "initial" to "connecting".
   *
   * @throws IllegalStateException if the current status is not initial
   */
  fun onConnecting() {
    lock.withLock {
      if (status != ClientModeDebuggerStatus.INITIAL) throw IllegalStateException()

      status = ClientModeDebuggerStatus.CONNECTING

      condition.signalAll()
    }
  }

  /**
   * Switches the current status from "connecting" to "connected". Returns
   * whether the switch succeeded.
   */
  fun onConnected(): Boolean =
    lock.withLock {
      if (status == ClientModeDebuggerStatus.CONNECTING) {
        status = ClientModeDebuggerStatus.CONNECTED

        condition.signalAll()

        return@withLock true
      }
      else {
        return@withLock false
      }
    }

  /**
   * Switches the current status to "disconnecting" unconditionally.
   */
  fun onDisconnectionInitiated() {
    lock.withLock {
      status = ClientModeDebuggerStatus.DISCONNECTION_INITIATED

      condition.signalAll()
    }
  }

  /**
   * Causes the current thread to wait until the status is changed from
   * "connecting" to "connected" or "disconnected".
   *
   * Returns `true` if the status becomes "connected" and `false` otherwise.
   *
   * @throws IllegalStateException if the current state is "initial"
   */
  fun awaitWhileConnecting(): Boolean =
    lock.withLock {
      if (status == ClientModeDebuggerStatus.INITIAL) throw IllegalStateException()

      while (status == ClientModeDebuggerStatus.CONNECTING) {
        condition.await()
      }

      return@awaitWhileConnecting status == ClientModeDebuggerStatus.CONNECTED
    }
}