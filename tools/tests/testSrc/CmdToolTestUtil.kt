package com.intellij.tools.cmd

import com.intellij.openapi.application.PathManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

data class ToolResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
) {
  val combinedOutput: String get() = (stdout + "\n" + stderr).trim()
}

object CmdToolTestUtil {
  val IS_WINDOWS: Boolean = System.getProperty("os.name").lowercase().startsWith("win")

  fun resolveToolsDir(): Path {
    val toolsDir = Path.of(PathManager.getCommunityHomePath()).resolve("tools")
    check(toolsDir.isDirectory()) { "Cannot find community/tools directory from ${PathManager.getCommunityHomePath()}" }
    return toolsDir
  }

  fun buildCommand(toolsDir: Path, scriptName: String, vararg args: String): List<String> {
    val script = toolsDir.resolve(scriptName).toAbsolutePath().toString()
    return if (IS_WINDOWS) {
      listOf("cmd.exe", "/c", script) + args
    }
    else {
      listOf(script) + args
    }
  }

  fun execute(
    command: List<String>,
    timeoutSeconds: Long = 300,
    workingDir: Path? = null,
    env: Map<String, String>? = null,
  ): ToolResult {
    val pb = ProcessBuilder(command)
    if (workingDir != null) {
      pb.directory(workingDir.toFile())
    }
    if (env != null) {
      pb.environment().putAll(env)
    }
    val process = pb.start()
    val stdoutFuture = CompletableFuture.supplyAsync({ process.inputStream.bufferedReader().readText() }, AppExecutorUtil.getAppExecutorService())
    val stderrFuture = CompletableFuture.supplyAsync({ process.errorStream.bufferedReader().readText() }, AppExecutorUtil.getAppExecutorService())
    val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!finished) {
      process.destroyForcibly()
      error("Process timed out after ${timeoutSeconds}s: ${command.joinToString(" ")}")
    }
    return ToolResult(
      exitCode = process.exitValue(),
      stdout = stdoutFuture.get(10, TimeUnit.SECONDS).trim(),
      stderr = stderrFuture.get(10, TimeUnit.SECONDS).trim(),
    )
  }

  fun parseToolVersion(scriptName: String, variableName: String = "TOOL_VERSION"): String {
    val toolsDir = resolveToolsDir()
    val content = toolsDir.resolve(scriptName).readText()
    val regex = Regex("""(?:export\s+)?$variableName="([^"]+)"""")
    val match = regex.find(content) ?: error("Cannot find $variableName in $scriptName")
    return match.groupValues[1]
  }

  fun runTool(scriptName: String, vararg args: String, timeoutSeconds: Long = 300, workingDir: Path? = null): ToolResult {
    val toolsDir = resolveToolsDir()
    val command = buildCommand(toolsDir, scriptName, *args)
    return execute(command, timeoutSeconds = timeoutSeconds, workingDir = workingDir)
  }
}
