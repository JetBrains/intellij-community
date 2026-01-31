// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.utils.threadDumpParser

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls

open class ThreadState(name: String?, state: String) {
  private val myName: String?
  private val myState: String
  private var myStackTrace: String? = null
  private var myEmptyStackTrace = false
  private var myJavaThreadState: String? = null
  private var myThreadStateDetail: String? = null
  private var myExtraState: String? = null
  private var isDaemon = false
  private val myThreadsWaitingForMyLock: MutableSet<ThreadState?> = HashSet<ThreadState?>()
  private val myDeadlockedThreads: MutableSet<ThreadState?> = HashSet<ThreadState?>()
  private var ownableSynchronizers: String? = null

  private var myOperation: ThreadOperation? = null
  private var myKnownJDKThread: Boolean? = null
  private var myStackDepth = 0

  init {
    myName = name
    myState = state.trim { it <= ' ' }
  }

  open fun getName(): @NlsSafe String? {
    return myName
  }

  open fun getStackTrace(): @NlsSafe String {
    return myStackTrace ?: ""
  }

  fun setStackTrace(stackTrace: String, isEmpty: Boolean) {
    myStackTrace = stackTrace
    myEmptyStackTrace = isEmpty
    myKnownJDKThread = null
    myStackDepth = StringUtil.countNewLines(myStackTrace!!)
  }

  fun getStackDepth(): Int {
    return myStackDepth
  }

  fun isKnownJDKThread(): Boolean {
    val stackTrace = myStackTrace
    if (stackTrace == null) {
      return false
    }
    if (myKnownJDKThread == null) {
      myKnownJDKThread = ThreadDumpParser.isKnownJdkThread(stackTrace)
    }
    return myKnownJDKThread!!
  }

  override fun toString(): String {
    return myName!!
  }

  fun setJavaThreadState(javaThreadState: String?) {
    myJavaThreadState = javaThreadState
  }

  fun setThreadStateDetail(threadStateDetail: @NonNls String?) {
    myThreadStateDetail = threadStateDetail
  }

  open fun getJavaThreadState(): String? {
    return myJavaThreadState
  }

  open fun getThreadStateDetail(): @NlsSafe String? {
    if (myOperation != null) {
      return myOperation.toString()
    }
    return myThreadStateDetail
  }

  open fun isEmptyStackTrace(): Boolean {
    return myEmptyStackTrace
  }

  fun setExtraState(extraState: String?) {
    myExtraState = extraState
  }

  open fun isSleeping(): Boolean {
    return "sleeping" == getThreadStateDetail() ||
           (("parking" == getThreadStateDetail() || "waiting on condition" == myState) && isThreadPoolExecutor())
  }

  private fun isThreadPoolExecutor(): Boolean {
    return myStackTrace!!.contains("java.util.concurrent.ScheduledThreadPoolExecutor\$DelayedWorkQueue.take") ||
           myStackTrace!!.contains("java.util.concurrent.ThreadPoolExecutor.getTask")
  }

  open fun isAwaitedBy(thread: ThreadState?): Boolean {
    return myThreadsWaitingForMyLock.contains(thread)
  }

  fun addWaitingThread(thread: ThreadState) {
    myThreadsWaitingForMyLock.add(thread)
  }

  fun addDeadlockedThread(thread: ThreadState?) {
    myDeadlockedThreads.add(thread)
  }

  open fun getOperation(): ThreadOperation? {
    return myOperation
  }

  fun setOperation(operation: ThreadOperation?) {
    myOperation = operation
  }

  open fun isEDT(): Boolean {
    val name = getName()
    return isEDT(name)
  }

  fun getOwnableSynchronizers(): String? {
    return ownableSynchronizers
  }

  fun setOwnableSynchronizers(ownableSynchronizers: String?) {
    this.ownableSynchronizers = ownableSynchronizers
  }

  fun setDaemon(daemon: Boolean) {
    isDaemon = daemon
  }

  companion object {
    fun isEDT(name: String?): Boolean {
      return ThreadDumper.isEDT(name)
    }
  }
}
