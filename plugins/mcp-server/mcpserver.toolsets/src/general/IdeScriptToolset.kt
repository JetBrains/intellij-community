@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.ide.script.IdeScriptEngine
import com.intellij.ide.script.IdeScriptEngineManager
import com.intellij.ide.script.IdeScriptException
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.StringWriter
import kotlin.time.Duration.Companion.seconds

class IdeScriptToolset : McpToolset {
  override fun displayName(): String = McpServerBundle.message("toolset.display.name.ide.script")

  override fun displayDescription(toolName: String): String? = McpServerBundle.message("tool.description.$toolName")

  @McpTool
  @McpDescription("""
      |Runs Kotlin code in the running JetBrains IDE process, with the full IDE classpath and live project state.
      |Last resort. Use a dedicated tool when one covers the task.
      |For state nothing else exposes, such as the resolved workspace model, registered extensions, or a service's state.
      |A `project` variable is bound to the project this call targets.
      |The value of the last expression is returned, so end with an expression, not a declaration.
      |Most IDE API reads need `runReadAction { ... }`.
      |Code runs in the live IDE. Avoid long-running work and unintended mutations.
      |Calls share one session, so declarations survive. `newSession` clears it.
      |The first call is slower: the IDE resolves a kotlinc distribution before compiling.
      |A script that runs past the timeout is abandoned, but it keeps running in the IDE.
""")
  suspend fun run_ide_script(
    @McpDescription("The Kotlin script source")
    script: String,
    @McpDescription("Pass true when a declaration from an earlier call conflicts, or the session is broken")
    newSession: Boolean = false,
    @McpDescription("Seconds to wait for the script. The first call needs more, because it also compiles.")
    timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
  ): IdeScriptRunResult {
    val context = currentCoroutineContext()
    val project = context.project
    context.reportToolActivity(McpServerBundle.message("tool.activity.running.ide.script"))

    val service = IdeScriptEngineService.getInstance()
    val output = StringWriter()
    // `eval` blocks and ignores cancellation, so run it detached and wait on the handle instead.
    // A timeout then returns without the script, which keeps running until it ends by itself.
    val running = service.evalAsync(project, newSession, script, output)
    return try {
      val value = withTimeout(timeoutSeconds.seconds) { running.await() }
      IdeScriptRunResult(success = true, output = output.toString(), result = value?.toString())
    }
    catch (_: TimeoutCancellationException) {
      service.dropEngine()
      IdeScriptRunResult(
        success = false,
        output = output.toString(),
        error = "The script did not finish in $timeoutSeconds s. It still runs in the IDE. " +
                "The session is dropped, so the next call starts a new one.",
      )
    }
    catch (e: IdeScriptException) {
      IdeScriptRunResult(
        success = false,
        output = output.toString(),
        error = ExceptionUtil.getThrowableText(e.cause ?: e),
      )
    }
  }
}

private const val DEFAULT_TIMEOUT_SECONDS: Int = 60

@Serializable
data class IdeScriptRunResult(
  val success: Boolean,
  val output: String,
  val result: String? = null,
  val error: String? = null,
)

@Service(Service.Level.APP)
internal class IdeScriptEngineService(private val scope: CoroutineScope) {
  private var engine: IdeScriptEngine? = null
  private var primedProject: String? = null

  /** Runs [script] outside the caller, so a caller that gives up does not wait for a script that never ends. */
  fun evalAsync(project: Project, newSession: Boolean, script: String, output: StringWriter): Deferred<Any?> =
    scope.async(Dispatchers.IO) {
      // The first engine can unpack or download the kotlinc distribution, so stay off the EDT.
      val engine = engine(project, newSession)
      engine.stdOut = output
      engine.stdErr = output
      engine.eval(script)
    }

  @Synchronized
  fun dropEngine() {
    engine = null
    primedProject = null
  }

  @Synchronized
  fun engine(project: Project, newSession: Boolean): IdeScriptEngine {
    if (newSession) {
      engine = null
      primedProject = null
    }
    // A null loader asks the manager for AllPluginsLoader, which carries the whole IDE classpath.
    val current = engine
                  ?: IdeScriptEngineManager.getInstance().getEngineByFileExtension(KOTLIN_SCRIPT_EXTENSION, null)
                    ?.also { engine = it }
                  ?: mcpFail("This IDE runs no Kotlin script engine. " +
                             "It needs the Kotlin plugin or the Kotlin Scripting plugin.")

    // A later declaration shadows the earlier one, so a session can move to another project.
    if (primedProject != project.locationHash) {
      try {
        current.eval(primingScript(project))
      }
      catch (e: IdeScriptException) {
        mcpFail("The Kotlin script engine did not start: " + ExceptionUtil.getThrowableText(e.cause ?: e))
      }
      primedProject = project.locationHash
    }
    return current
  }

  // Matched on the location hash, because a name is neither unique nor safe to embed.
  private fun primingScript(project: Project): String {
    val locationHash = StringUtil.escapeStringCharacters(project.locationHash)
    return "val project: com.intellij.openapi.project.Project = " +
           "com.intellij.openapi.project.ProjectManager.getInstance().openProjects" +
           ".first { it.locationHash == \"$locationHash\" }"
  }

  companion object {
    private const val KOTLIN_SCRIPT_EXTENSION = "kts"

    fun getInstance(): IdeScriptEngineService = service()
  }
}
