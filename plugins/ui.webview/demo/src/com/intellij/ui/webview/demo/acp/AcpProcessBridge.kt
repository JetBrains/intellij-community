// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.demo.acp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Spawns a local ACP agent process and pipes its newline-delimited JSON-RPC stdio across the WebView bridge:
 * every stdout line is forwarded to the page via [AcpBridgePageApi.onAgentStdout]; lines from the page are written
 * to the process stdin. The ACP protocol itself runs in the TS SDK inside the webview.
 */
internal class AcpProcessBridge(
  private val project: Project,
  private val scope: CoroutineScope,
  private val pageApi: AcpBridgePageApi,
) {
  @Volatile private var process: Process? = null
  @Volatile private var writer: BufferedWriter? = null
  @Volatile private var readerJob: Job? = null
  // Bumped on every start/stop; the reader emits onAgentExit only for the still-current generation, so a deliberate
  // stop or restart (e.g. re-spawn with credentials for an env_var auth method) does not surface as "agent exited".
  @Volatile private var generation = 0

  // ProcessBuilder requires a java.io.File working directory; this is a local-only demo spawn (GeneralCommandLine is not on the demo classpath).
  @Suppress("IO_FILE_USAGE")
  @Synchronized
  fun start(agent: AcpAgent, extraEnv: Map<String, String> = emptyMap()): String {
    stop()
    val myGeneration = ++generation
    val command = buildList {
      add(agent.command)
      addAll(agent.args)
    }
    val builder = ProcessBuilder(command)
    val cwd = project.basePath
    if (cwd != null) builder.directory(java.io.File(cwd))
    builder.environment().putAll(agent.env)
    // Credentials entered for an env_var auth method are injected here on re-spawn; they win over the config env.
    builder.environment().putAll(extraEnv)
    val started = builder.start()
    process = started
    writer = BufferedWriter(OutputStreamWriter(started.outputStream, StandardCharsets.UTF_8))

    readerJob = scope.launch(Dispatchers.IO) {
      try {
        started.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
          while (true) {
            val line = reader.readLine() ?: break
            pageApi.onAgentStdout(LineDto(line))
          }
        }
      }
      catch (t: Throwable) {
        LOG.warn("[${agent.name}] ACP agent stdout reader stopped", t)
      }
      finally {
        if (generation == myGeneration) {
          val code = runCatching { started.exitValue() }.getOrNull()
          pageApi.onAgentExit(ExitDto(code))
        }
      }
    }

    // Drain stderr into the log so a misconfigured agent is diagnosable.
    scope.launch(Dispatchers.IO) {
      runCatching {
        started.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { LOG.debug("[${agent.name} stderr] $it") }
      }
    }

    return cwd ?: System.getProperty("user.dir")
  }

  @Synchronized
  fun send(line: String) {
    val target = writer ?: return
    try {
      target.write(line)
      target.write("\n")
      target.flush()
    }
    catch (t: Throwable) {
      LOG.warn("Failed to write to ACP agent stdin", t)
    }
  }

  @Synchronized
  fun stop() {
    generation++
    readerJob?.cancel()
    readerJob = null
    runCatching { writer?.close() }
    writer = null
    process?.destroy()
    process = null
  }

  private companion object {
    private val LOG = logger<AcpProcessBridge>()
  }
}
