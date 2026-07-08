// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyLayouts.C_BYTE
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttyResult
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttySysLogLevel
import com.intellij.terminal.emulator.impl.ghostty.bindings.GhosttySysOption
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVt
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.charset.StandardCharsets

/**
 * Reroutes libghostty-vt's internal `std.log` output into the idea.log. Otherwise,
 * logging will be lost, and native-side problems (bad escapes, image/decode failures, internal errors)
 * are invisible from the IDE.
 *
 * A single process-global upcall ([onNativeLog]) is installed once.
 *
 * Notes:
 * - The hook is process-global and installed once (its upcall stub lives on [LibGhosttyVt]'s
 *   library-lifetime arena), so the key is read once when the native library is first loaded.
 * - With a release-built dylib only INFO and above are emitted; DEBUG is compiled out of the library.
 * - Messages larger than the library's internal buffer (2048 bytes) arrive chunked across multiple
 *   callback invocations, so each chunk is forwarded as its own line.
 */
internal object GhosttyLog {

  private val LOG: Logger = logger<GhosttyLog>()

  /**
   * Receives a native log message. Invoked from [onNativeLog], so it may run on any thread.
   */
  fun interface Handler {
    fun onLog(level: GhosttySysLogLevel, scope: String, message: String)
  }

  /** The default handler: routes each line into idea.log with a faithful level mapping. */
  val defaultHandler: Handler = Handler { level, scope, message ->
    val text = formatMessage(scope, message)
    when (level) {
      GhosttySysLogLevel.ERROR -> LOG.error(text)
      GhosttySysLogLevel.WARNING -> LOG.warn(text)
      GhosttySysLogLevel.INFO -> LOG.info(text)
      GhosttySysLogLevel.DEBUG -> LOG.debug(text)
    }
  }

  /**
   * The active handler for decoded native log lines. Defaults to [defaultHandler] (idea.log); tests
   * replace it to observe messages and should restore it afterward.
   */
  @Volatile
  private var handler: Handler = defaultHandler

  fun doWithHandler(newHandler: Handler, action: () -> Unit) {
    val previous = handler
    handler = newHandler
    try {
      action()
    }
    finally {
      handler = previous
    }
  }

  /**
   * A single upcall stub bound to [onNativeLog], created lazily once and reused: it lives on the
   * library's process-lifetime arena, so re-creating it per [installIfEnabled] would leak.
  */
  private val nativeLogStub: MemorySegment by lazy {
    val handle = MethodHandles.lookup().bind(
      this, "onNativeLog",
      MethodType.methodType(
        Void.TYPE,
        MemorySegment::class.java, Integer.TYPE,
        MemorySegment::class.java, java.lang.Long.TYPE,
        MemorySegment::class.java, java.lang.Long.TYPE,
      ),
    )
    LibGhosttyVt.logCallbackUpcallStub(handle)
  }

  /**
   * Install the process-global native log callback if the `terminal.ghostty.native.logging` registry
   * key is enabled. Fully defensive: any failure (Registry unavailable during early init, missing symbol,
   * etc.) is swallowed and reported as "not installed" so it can never break terminal creation.
   */
  fun installIfEnabled() {
    try {
      if (isLoggingEnabled()) {
        val result = LibGhosttyVt.sysSet(GhosttySysOption.LOG.code, nativeLogStub)
        if (result != GhosttyResult.SUCCESS) {
          LOG.warn("ghostty_sys_set(LOG) returned $result; libghostty-vt native logs will not be captured")
        }
        else {
          LOG.info("libghostty-vt native logging enabled")
        }
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to capture the libghostty-vt native logging", t)
    }
  }

  fun isLoggingEnabled(): Boolean = Registry.`is`("terminal.ghostty.native.logging", true)

  /**
   * Upcall target invoked from native code whenever libghostty-vt emits a log message. May be called
   * from any thread. Must never throw across the FFM boundary (an escaping exception crashes the JVM),
   * hence the blanket catch.
   */
  @Suppress("unused", "UNUSED_PARAMETER")
  private fun onNativeLog(
    userdata: MemorySegment,
    level: Int,
    scope: MemorySegment,
    scopeLen: Long,
    message: MemorySegment,
    messageLen: Long,
  ) {
    try {
      handler.onLog(GhosttySysLogLevel.of(level), readUtf8(scope, scopeLen), readUtf8(message, messageLen))
    }
    catch (_: Throwable) {
      // Never propagate out of a native upcall.
    }
  }

  /** Format a native log line as `[scope] message`; the scope prefix is omitted when unscoped. */
  fun formatMessage(scope: String, message: String): String {
    return if (scope.isEmpty()) message else "[$scope] $message"
  }

  /** Decode [len] UTF-8 bytes at [ptr]; empty when the pointer carries no bytes. */
  fun readUtf8(ptr: MemorySegment, len: Long): String {
    if (len <= 0L) {
      return ""
    }
    return String(ptr.reinterpret(len).toArray(C_BYTE), StandardCharsets.UTF_8)
  }
}
