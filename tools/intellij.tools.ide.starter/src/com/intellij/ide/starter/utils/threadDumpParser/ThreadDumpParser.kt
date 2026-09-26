// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.utils.threadDumpParser

import com.intellij.diagnostic.EventCountDumper
import com.intellij.diagnostic.isCoroutineDumpHeader
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls
import java.util.regex.Matcher
import java.util.regex.Pattern

internal object ThreadDumpParser {
  private val ourThreadStartPattern: Pattern = Pattern.compile(
    "^\"(.+)\".+(prio=\\d+ (?:os_prio=[^\\s]+ )?.*tid=[^\\s]+ nid=[^\\s]+|[Ii][Dd]=\\d+) ([^\\[]+)")
  private val ourForcedThreadStartPattern: Pattern = Pattern.compile("^Thread (\\d+): \\(state = (.+)\\)")
  private val ourYourkitThreadStartPattern: Pattern = Pattern.compile("(.+) \\[([A-Z_, ]*)]")
  private val ourYourkitThreadStartPattern2: Pattern = Pattern.compile("(.+) (?:State:)? (.+) CPU usage on sample: .+")
  private val ourThreadStatePattern: Pattern = Pattern.compile("java\\.lang\\.Thread\\.State: (.+) \\((.+)\\)")
  private val ourThreadStatePattern2: Pattern = Pattern.compile("java\\.lang\\.Thread\\.State: (.+)")
  private val ourWaitingForLockPattern: Pattern = Pattern.compile("- waiting (on|to lock) <(.+)>")
  private val ourParkingToWaitForLockPattern: Pattern = Pattern.compile("- parking to wait for {2}<(.+)>")
  private const val PUMP_EVENT: @NonNls String = "java.awt.EventDispatchThread.pumpOneEventForFilters"
  private val ourIdleTimerThreadPattern: Pattern = Pattern.compile(
    "java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at java\\.util\\.TimerThread\\.mainLoop")
  private val ourIdleSwingTimerThreadPattern: Pattern = Pattern.compile(
    "java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at javax\\.swing\\.TimerQueue\\.run")
  private const val AT_JAVA_LANG_OBJECT_WAIT = "at java.lang.Object.wait("
  private val ourLockedOwnableSynchronizersPattern: Pattern = Pattern.compile("- <(0x[\\da-f]+)> \\(.*\\)")


  fun parse(threadDump: String): MutableList<ThreadState> {
    val result: MutableList<ThreadState> = ArrayList<ThreadState>()
    val lastThreadStack = StringBuilder()
    var lastThreadState: ThreadState? = null
    var expectingThreadState = false
    var haveNonEmptyStackTrace = false
    var coroutineDump: StringBuilder? = null
    for (line: @NonNls String in StringUtil.tokenize(threadDump, "\r\n")) {
      if (EventCountDumper.EVENT_COUNTS_HEADER == line) {
        break
      }
      if (isCoroutineDumpHeader(line)) {
        coroutineDump = StringBuilder()
      }
      if (coroutineDump != null) {
        coroutineDump.append(line).append("\n")
        continue
      }
      if (line.startsWith("============") || line.contains("Java-level deadlock")) {
        break
      }
      val state = tryParseThreadStart(line.trim { it <= ' ' })
      if (state != null) {
        if (lastThreadState != null) {
          lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace)
        }
        lastThreadState = state
        result.add(lastThreadState)
        lastThreadStack.setLength(0)
        haveNonEmptyStackTrace = false
        lastThreadStack.append(line).append("\n")
        expectingThreadState = true
      }
      else {
        var parsedThreadState = false
        if (expectingThreadState) {
          expectingThreadState = false
          parsedThreadState = tryParseThreadState(line, lastThreadState!!)
        }
        lastThreadStack.append(line).append("\n")
        if (!parsedThreadState && line.trim { it <= ' ' }.startsWith("at")) {
          haveNonEmptyStackTrace = true
        }
      }
    }
    if (lastThreadState != null) {
      lastThreadState.setStackTrace(lastThreadStack.toString(), !haveNonEmptyStackTrace)
    }
    for (threadState in result) {
      inferThreadStateDetail(threadState)
    }
    for (threadState in result) {
      val lockId = findWaitingForLock(threadState.getStackTrace())
      var lockOwner = findLockOwner(result, lockId, true)
      if (lockOwner == null) {
        lockOwner = findLockOwner(result, lockId, false)
      }
      if (lockOwner != null) {
        if (threadState.isAwaitedBy(lockOwner)) {
          threadState.addDeadlockedThread(lockOwner)
          lockOwner.addDeadlockedThread(threadState)
        }
        lockOwner.addWaitingThread(threadState)
      }
    }
    sortThreads(result)
    if (coroutineDump != null) {
      val coroutineState = ThreadState("Coroutine dump", "undefined")
      coroutineState.setStackTrace(coroutineDump.toString(), false)
      result.add(coroutineState)
    }
    return result
  }

  private fun findLockOwner(result: MutableList<out ThreadState>, lockId: String?, ignoreWaiting: Boolean): ThreadState? {
    if (lockId == null) return null

    val marker = "- locked <" + lockId + ">"
    for (lockOwner in result) {
      val trace = lockOwner.getStackTrace()
      if (trace.contains(marker) && (!ignoreWaiting || !trace.contains(AT_JAVA_LANG_OBJECT_WAIT))) {
        return lockOwner
      }
    }
    for (lockOwner in result) {
      if (lockOwner.getOwnableSynchronizers() != null && lockOwner.getOwnableSynchronizers() == lockId) {
        return lockOwner
      }
    }
    return null
  }

  fun sortThreads(result: MutableList<out ThreadState>) {
    result.sortWith({ o1: ThreadState, o2: ThreadState -> getInterestLevel(o2) - getInterestLevel(o1) })
  }

  private fun findLockedOwnableSynchronizers(stackTrace: String): String? {
    val m = ourLockedOwnableSynchronizersPattern.matcher(stackTrace)
    if (m.find()) {
      return m.group(1)
    }
    return null
  }

  private fun findWaitingForLock(stackTrace: String): String? {
    var m = ourWaitingForLockPattern.matcher(stackTrace)
    if (m.find()) {
      return m.group(2)
    }
    m = ourParkingToWaitForLockPattern.matcher(stackTrace)
    if (m.find()) {
      return m.group(1)
    }
    return null
  }

  private fun getInterestLevel(state: ThreadState): Int {
    if (state.isEmptyStackTrace()) return -10
    if (state.isKnownJDKThread()) return -5
    if (state.isSleeping()) {
      return -2
    }
    if (state.getOperation() == ThreadOperation.Socket) {
      return -1
    }
    return state.getStackDepth()
  }

  fun isKnownJdkThread(stackTrace: String): Boolean {
    return stackTrace.contains("java.lang.ref.Reference\$ReferenceHandler.run") ||
           stackTrace.contains("java.lang.ref.Finalizer\$FinalizerThread.run") ||
           stackTrace.contains("sun.awt.AWTAutoShutdown.run") ||
           stackTrace.contains("sun.java2d.Disposer.run") ||
           stackTrace.contains("sun.awt.windows.WToolkit.eventLoop") ||
           ourIdleTimerThreadPattern.matcher(stackTrace).find() ||
           ourIdleSwingTimerThreadPattern.matcher(stackTrace).find()
  }

  fun inferThreadStateDetail(threadState: ThreadState) {
    val stackTrace: @NonNls String = threadState.getStackTrace()
    if (stackTrace.contains("at java.net.PlainSocketImpl.socketAccept") ||
        stackTrace.contains("at java.net.PlainDatagramSocketImpl.receive") ||
        stackTrace.contains("at java.net.SocketInputStream.socketRead") ||
        stackTrace.contains("at java.net.PlainSocketImpl.socketConnect")
    ) {
      threadState.setOperation(ThreadOperation.Socket)
    }
    else if (stackTrace.contains("at java.io.FileInputStream.readBytes")) {
      threadState.setOperation(ThreadOperation.IO)
    }
    else if (stackTrace.contains("at java.lang.Thread.sleep")) {
      val javaThreadState = threadState.getJavaThreadState()
      if (Thread.State.RUNNABLE.name != javaThreadState) {
        threadState.setThreadStateDetail("sleeping") // JDK 1.6 sets this explicitly, but JDK 1.5 does not
      }
    }
    if (threadState.isEDT()) {
      if (stackTrace.contains("java.awt.EventQueue.getNextEvent")) {
        threadState.setThreadStateDetail("idle")
      }
      var modality = 0
      var pos = 0
      while (true) {
        pos = stackTrace.indexOf(PUMP_EVENT, pos)
        if (pos < 0) break
        modality++
        pos += PUMP_EVENT.length
      }
      threadState.setExtraState("modality level " + modality)
    }
    threadState.setOwnableSynchronizers(findLockedOwnableSynchronizers(threadState.getStackTrace()))
  }

  private fun tryParseThreadStart(line: String): ThreadState? {
    var line = line
    var m = ourThreadStartPattern.matcher(line)
    if (m.find()) {
      val state = ThreadState(m.group(1), m.group(3))
      if (line.contains(" daemon ")) {
        state.setDaemon(true)
      }
      return state
    }

    m = ourForcedThreadStartPattern.matcher(line)
    if (m.matches()) {
      return ThreadState(m.group(1), m.group(2))
    }

    val daemon = line.contains(" [DAEMON]")
    if (daemon) {
      line = StringUtil.replace(line, " [DAEMON]", "")
    }

    m = matchYourKit(line) ?: return null
    val state = ThreadState(m.group(1), m.group(2))
    state.setDaemon(daemon)
    return state
  }

  private fun matchYourKit(line: String): Matcher? {
    if (line.contains("[")) {
      val m = ourYourkitThreadStartPattern.matcher(line)
      if (m.matches()) return m
    }

    if (line.contains("CPU usage on sample:")) {
      val m = ourYourkitThreadStartPattern2.matcher(line)
      if (m.matches()) return m
    }

    return null
  }

  private fun tryParseThreadState(line: String, threadState: ThreadState): Boolean {
    var m = ourThreadStatePattern.matcher(line)
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1))
      threadState.setThreadStateDetail(m.group(2).trim { it <= ' ' })
      return true
    }
    m = ourThreadStatePattern2.matcher(line)
    if (m.find()) {
      threadState.setJavaThreadState(m.group(1))
      return true
    }
    return false
  }
}
