@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.ide.script.IdeScriptEngine
import com.intellij.ide.script.IdeScriptEngineManager
import com.intellij.ide.script.IdeScriptException
import com.intellij.lang.Language
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.elicitation.ElicitationMessagePart
import com.intellij.mcpserver.elicitation.ElicitationResult
import com.intellij.mcpserver.elicitation.elicitationProvider
import com.intellij.mcpserver.elicitation.requestElicitation
import com.intellij.mcpserver.mcpCallInfoOrNull
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.toolsets.util.CommandExecutionRejectedException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ExceptionUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Action
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds

class IdeScriptToolset : McpToolset {
  override fun isEnabled(): Boolean = isUnattended() || Registry.`is`(IDE_SCRIPT_TOOLSET_REGISTRY_KEY, false)

  override fun displayName(): String = McpServerBundle.message("toolset.display.name.ide.script")

  override fun displayDescription(toolName: String): String? = McpServerBundle.message("tool.description.$toolName")

  @McpToolHints(readOnlyHint = FALSE, destructiveHint = TRUE, openWorldHint = TRUE)
  @McpTool
  @McpDescription("""
      |Runs Kotlin code in the running JetBrains IDE process, with the full IDE classpath and live project state.
      |Works only when the user turns on Brave Mode in the IDE as MCP Server settings.
      |The user confirms each script before it runs, unless the user allowed all scripts of the current MCP session.
      |If the IDE changed the script, a failed result holds the text that ran in `formattedScript`. The line numbers in `error` refer to that text.
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
    val prepared = prepareIdeScript(project, context.mcpCallInfoOrNull?.sessionId, script)
    // The line numbers in an error refer to the formatted text, so the agent gets that text with the error.
    val formattedScript = prepared.formattedScript

    val service = IdeScriptEngineService.getInstance()
    val output = StringWriter()
    // `eval` blocks and ignores cancellation, so run it detached and wait on the handle instead.
    // A timeout then returns without the script, which keeps running until it ends by itself.
    val running = service.evalAsync(project, newSession, prepared.text, output)
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
        formattedScript = formattedScript,
      )
    }
    catch (e: IdeScriptException) {
      IdeScriptRunResult(
        success = false,
        output = output.toString(),
        error = ExceptionUtil.getThrowableText(e.cause ?: e),
        formattedScript = formattedScript,
      )
    }
  }
}

private const val DEFAULT_TIMEOUT_SECONDS: Int = 60

/**
 * Lets an evaluation pipeline run scripts without Brave Mode and without a confirmation.
 * It also turns on the toolset. Only a VM option sets it, so the IDE settings cannot turn it on by mistake.
 */
private const val UNATTENDED_PROPERTY: String = "idea.mcp.server.ide.script.unattended"

private fun isUnattended(): Boolean = SystemProperties.getBooleanProperty(UNATTENDED_PROPERTY, false)

/**
 * The script that the IDE may run.
 */
@ApiStatus.Internal
class PreparedIdeScript(
  /** The text to run. The user saw this text in the confirmation. */
  val text: String,
  /** The same as [text] if the IDE formatted the script, else `null`. */
  val formattedScript: String?,
)

@ApiStatus.Internal
enum class IdeScriptDecision { ALLOW, ALLOW_FOR_SESSION, DENY }

/**
 * Checks that the IDE may run [script] and returns the text to run.
 * Without Brave Mode it fails. Then [confirm] asks the user, unless the user allowed all scripts of the MCP session [sessionId].
 * [confirm] gets the text to run and tells if the user can allow the session.
 * In the unattended mode it does not check or format the script.
 */
@ApiStatus.Internal
suspend fun prepareIdeScript(
  project: Project,
  sessionId: String?,
  script: String,
  confirm: suspend (script: String, canAllowForSession: Boolean) -> IdeScriptDecision = { text, canAllowForSession ->
    confirmScript(project, text, canAllowForSession)
  },
): PreparedIdeScript {
  val trimmed = trimBlankLines(script)
  if (isUnattended()) return PreparedIdeScript(trimmed, formattedScript = null)
  if (!McpServerSettings.getInstance().enableBraveMode) {
    mcpFail("Brave Mode is off, so the IDE runs no scripts. " +
            "Only the user can turn on Brave Mode in the IDE as MCP Server settings. Do not retry before that.")
  }
  // The user sees the same text that runs, so the line numbers in an error match the dialog.
  val source = formatScript(project, trimmed)
  checkScriptConsent(sessionId, source, confirm)
  return PreparedIdeScript(source, formattedScript = source.takeIf { it != trimmed })
}

/**
 * Removes the blank lines at the start and at the end of [script], and converts the line separators to `\n`.
 */
private fun trimBlankLines(script: String): String {
  return StringUtil.convertLineSeparators(script).lines()
    .dropWhile { it.isBlank() }
    .dropLastWhile { it.isBlank() }
    .joinToString("\n")
}

/**
 * Formats [script] with the Kotlin code style, so the user can read a script that an agent sends in one line.
 * The formatter changes only the whitespace. Without the Kotlin plugin, or if the formatter fails, it returns [script].
 */
private suspend fun formatScript(project: Project, script: String): String {
  val fileType = FileTypeManager.getInstance().getFileTypeByExtension(KOTLIN_SCRIPT_EXTENSION)
  if (fileType !is LanguageFileType || fileType.language == PlainTextLanguage.INSTANCE) return script
  return try {
    // The file is not physical, so a read action is enough to change it.
    readAction {
      val file = PsiFileFactory.getInstance(project).createFileFromText("script.$KOTLIN_SCRIPT_EXTENSION", fileType, script)
      CodeStyleManager.getInstance(project).reformat(file).text
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    logger<IdeScriptToolset>().warn("Cannot format the IDE script", e)
    script
  }
}

/**
 * Throws [CommandExecutionRejectedException] if the user does not allow [script].
 * The user can allow all later scripts of the MCP session [sessionId].
 * Brave Mode does not skip this step, because the script runs with the permissions of the IDE process.
 */
private suspend fun checkScriptConsent(
  sessionId: String?,
  script: String,
  confirm: suspend (script: String, canAllowForSession: Boolean) -> IdeScriptDecision,
) {
  val consents = IdeScriptSessionConsents.getInstance()
  if (sessionId != null && consents.isAllowed(sessionId)) return
  when (confirm(script, sessionId != null)) {
    IdeScriptDecision.ALLOW -> Unit
    IdeScriptDecision.ALLOW_FOR_SESSION -> if (sessionId != null) consents.allow(sessionId)
    IdeScriptDecision.DENY -> throw CommandExecutionRejectedException()
  }
}

/**
 * Asks the user to allow [script]. It uses MCP elicitation when the client supports it, else the IDE dialog.
 */
private suspend fun confirmScript(project: Project, script: String, canAllowForSession: Boolean): IdeScriptDecision {
  val notificationText = McpServerBundle.message("label.do.you.want.to.run.ide.script")
  val elicitation = elicitationProvider()?.requestElicitation<IdeScriptConsentInput> {
    message {
      styled("$notificationText\n", color = ElicitationMessagePart.TextColor.RED)
      code(script, Language.findLanguageByID(KOTLIN_LANGUAGE_ID) ?: PlainTextLanguage.INSTANCE)
    }
    singleSelect(DECISION_FIELD) {
      required = true
      option(ALLOW_DECISION, McpServerBundle.message("command.execution.confirmation.allow"))
      if (canAllowForSession) {
        option(ALLOW_FOR_SESSION_DECISION, McpServerBundle.message("ide.script.confirmation.allow.for.session"))
      }
      option(DENY_DECISION, McpServerBundle.message("command.execution.confirmation.deny"))
    }
  }
  if (elicitation == null) {
    return withContext(Dispatchers.EDT) {
      val dialog = ScriptConfirmationDialog(project, notificationText, script, canAllowForSession)
      dialog.show()
      dialog.decision
    }
  }
  if (elicitation !is ElicitationResult.Accept) return IdeScriptDecision.DENY
  return when {
    elicitation.value.decision == ALLOW_DECISION -> IdeScriptDecision.ALLOW
    elicitation.value.decision == ALLOW_FOR_SESSION_DECISION && canAllowForSession -> IdeScriptDecision.ALLOW_FOR_SESSION
    else -> IdeScriptDecision.DENY
  }
}

/**
 * Shows [script] in a read-only Kotlin editor and asks the user to allow it.
 * The dialog has no Brave Mode option, because Brave Mode does not skip this step.
 */
@Suppress("SplitModeApiUsage") // the module already shows the confirmation dialog of askConfirmation
private class ScriptConfirmationDialog(
  private val project: Project,
  @NlsContexts.Label private val notificationText: String,
  private val script: String,
  canAllowForSession: Boolean,
) : DialogWrapper(project, true) {
  private val allowForSessionAction: Action? = if (canAllowForSession) {
    object : DialogWrapperAction(McpServerBundle.message("ide.script.confirmation.allow.for.session")) {
      override fun doAction(e: ActionEvent?) = close(ALLOW_FOR_SESSION_EXIT_CODE)
    }
  }
  else null

  val decision: IdeScriptDecision
    get() = when (exitCode) {
      OK_EXIT_CODE -> IdeScriptDecision.ALLOW
      ALLOW_FOR_SESSION_EXIT_CODE -> IdeScriptDecision.ALLOW_FOR_SESSION
      else -> IdeScriptDecision.DENY
    }

  init {
    title = McpServerBundle.message("dialog.title.confirm.command.execution")
    okAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.allow"))
    cancelAction.putValue(Action.NAME, McpServerBundle.message("command.execution.confirmation.deny"))
    init()
  }

  override fun createActions(): Array<Action> = listOfNotNull(allowForSessionAction, okAction, cancelAction).toTypedArray()

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        text(notificationText)
      }
      row {
        cell(scriptViewer(project, script))
          .align(Align.FILL)
      }.resizableRow()
    }
  }
}

/**
 * Shows the text of [script] as it is, with Kotlin syntax highlighting.
 * The document has no PSI file, so the IDE does not analyze the script.
 */
private fun scriptViewer(project: Project, script: String): EditorTextField {
  val fileType = FileTypeManager.getInstance().getFileTypeByExtension(KOTLIN_SCRIPT_EXTENSION)
  val document = EditorFactory.getInstance().createDocument(script)
  return EditorTextField(document, project, fileType, true, false).apply {
    setFontInheritedFromLAF(false)
    addSettingsProvider { editor ->
      editor.isEmbeddedIntoDialogWrapper = true
      editor.settings.isUseSoftWraps = true
      // An agent often sends the script in one line, so the wrap signs fill the whole text
      editor.settings.isPaintSoftWraps = false
      // The line numbers match the lines in a compilation error
      editor.settings.isLineNumbersShown = true
      editor.gutterComponentEx.isPaintBackground = false
      editor.setBorder(JBUI.Borders.merge(editor.scrollPane.border, JBUI.Borders.empty(6, 0, 6, 10), false))
      editor.setVerticalScrollbarVisible(true)
    }
    preferredSize = JBUI.size(600, 300)
  }
}

private const val IDE_SCRIPT_TOOLSET_REGISTRY_KEY: String = "mcp.ide.script.toolset.enabled"
private const val KOTLIN_LANGUAGE_ID: String = "kotlin"
private const val KOTLIN_SCRIPT_EXTENSION: String = "kts"
private const val DECISION_FIELD: String = "decision"
private const val ALLOW_DECISION: String = "allow"
private const val ALLOW_FOR_SESSION_DECISION: String = "allow_for_session"
private const val DENY_DECISION: String = "deny"
private const val ALLOW_FOR_SESSION_EXIT_CODE: Int = DialogWrapper.NEXT_USER_EXIT_CODE

@Serializable
private data class IdeScriptConsentInput(val decision: String = DENY_DECISION)

@Serializable
data class IdeScriptRunResult(
  val success: Boolean,
  val output: String,
  val result: String? = null,
  val error: String? = null,
  /** The text that ran, if the IDE formatted the script. The line numbers in [error] refer to it. */
  val formattedScript: String? = null,
)

/**
 * Keeps the MCP sessions in which the user allows all scripts.
 * The IDE never uses a session ID again, so the set does not forget an ID.
 */
@Service(Service.Level.APP)
internal class IdeScriptSessionConsents {
  private val allowedSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

  fun isAllowed(sessionId: String): Boolean = sessionId in allowedSessions

  fun allow(sessionId: String) {
    allowedSessions.add(sessionId)
  }

  companion object {
    fun getInstance(): IdeScriptSessionConsents = service()
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class IdeScriptEngineService(private val scope: CoroutineScope) {
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
    fun getInstance(): IdeScriptEngineService = service()
  }
}
