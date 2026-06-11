// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.windows

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.webview.impl.WebViewLogger
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.CoroutineContext

/**
 * Dedicated daemon thread that hosts every WebView2 STA call.
 *
 * The native side enters a blocking `GetMessageW` loop on this thread. Tasks
 * scheduled through [coroutineDispatcher] are marshalled in via
 * `PostThreadMessageW` and executed on this thread. The thread is shared by
 * every [WinWebViewEngine] in the JVM and lives until process exit.
 */
internal object WebView2Dispatcher {
  @Volatile
  private var threadTid: Long = 0

  // Held until the thread has cached its Win32 TID and its message queue is
  // ready to receive PostThreadMessageW. Dispatching before that point would
  // silently drop the task.
  private val startupLatch = CountDownLatch(1)

  private val thread: Thread = Thread {
    threadTid = WinWebView2Bridge.currentThreadId()
    startupLatch.countDown()
    try {
      WinWebView2Bridge.runMessageLoop()
    }
    finally {
      // Loop exited (WM_QUIT or system error). Drop the TID so any further
      // postTask attempts fail loudly instead of going to a dead thread.
      threadTid = 0
      WebViewLogger.LOG.warn("WebView2 dispatcher message loop has exited")
    }
  }.apply {
    name = "WebView2-Thread"
    isDaemon = true
    uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
      WebViewLogger.LOG.error("Uncaught exception in WebView2-Thread", e)
    }
  }

  val coroutineDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      startupLatch.await()
      val tid = threadTid
      if (tid == 0L) {
        WebViewLogger.LOG.warn("Dropping WebView2 task: dispatcher thread is no longer running")
        return
      }
      WinWebView2Bridge.postTask(block, tid)
    }
  }

  init {
    check(SystemInfo.isWindows) {
      "WebView2Dispatcher must only be used on Windows"
    }
    thread.start()
  }
}
