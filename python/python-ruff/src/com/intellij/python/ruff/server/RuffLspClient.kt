package com.intellij.python.ruff.server

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.python.lsp.core.PyLspService
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyToolChangeDebouncer
import com.intellij.python.lsp.core.pyLspModulesToServeWith
import com.intellij.python.ruff.RuffBundle
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffFolderFallback
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.RuffService
import com.intellij.python.ruff.RuffSettings
import com.intellij.python.ruff.codeinsight.actions.RuffDisableRuleForFileIntentionAction
import com.intellij.python.ruff.codeinsight.actions.RuffDisableRuleIntentionAction
import com.intellij.python.ruff.isRuffConfigEvent
import com.intellij.python.ruff.ruffFallbackConfig
import com.intellij.python.ruff.ruffFolderFallback
import com.intellij.python.ruff.ruffGetsProjectConfig
import com.intellij.python.ruff.ruffInitializationOptions
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.InitializeResult
import org.jetbrains.annotations.Nls

class RuffLspIntegrationProvider : PyLspToolIntegrationProvider() {
  /**
   * Ruff keeps one workspace for each folder and finds the configuration file of each file itself. It needs no
   * interpreter, so one server answers for every module that runs the same Ruff. A module whose environment pins
   * another Ruff version gets a server of its own, because one server runs one binary.
   */
  override fun getDescriptor(module: Module): RuffLspClientDescriptor {
    val servedModules = pyLspModulesToServeWith(module, RuffPyTool.getInstance())
    return RuffLspClientDescriptor(servedModules.first(), servedModules)
  }

  override fun pyTool(project: Project): PyLspTool<*> = RuffPyTool.getInstance()

  override val servesEveryModule: Boolean get() = true

  /**
   * Also restarts the Ruff servers when the project config of a workspace folder changes, see
   * [RuffLspClientDescriptor.createInitializationOptions].
   *
   * A Ruff server reads the `configuration` file of a folder only when it starts, and it ignores
   * `workspace/didChangeConfiguration`. A config that comes or goes can also change which folders need the project
   * config. So only a restart makes the servers current.
   */
  override fun subscribeOnChanges(pyTool: PyLspTool<*>, project: Project, parentDisposable: Disposable) {
    super.subscribeOnChanges(pyTool, project, parentDisposable)
    val configChanged = PyToolChangeDebouncer(project.service<PyLspService>().cs) { restartIfFallbackChanged(project) }
    project.messageBus.connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any(::isRuffConfigEvent)) configChanged.schedule()
      }
    })
  }

  private suspend fun restartIfFallbackChanged(project: Project) {
    val manager = LspClientManager.getInstance(project)
    val descriptors = readAction { manager.getClients(RuffLspIntegrationProvider::class.java) }
      .mapNotNull { it.descriptor as? RuffLspClientDescriptor }
    if (descriptors.none { it.fallbackIsStale() }) return
    project.service<PyLspService>().restartMutex.withLock {
      // Another restart can finish while this one waits for the lock. Its clients are current already.
      val current = readAction { manager.getClients(RuffLspIntegrationProvider::class.java) }
        .mapNotNull { it.descriptor as? RuffLspClientDescriptor }
      if (current.none { it.fallbackIsStale() }) return@withLock
      thisLogger().info("The project Ruff config of a workspace folder changed. Restarting the Ruff clients.")
      manager.stopAndRestartClientsIfNeeded(RuffLspIntegrationProvider::class.java)
    }
  }
}

class RuffLspClientDescriptor(
  module: Module,
  servedModules: List<Module> = listOf(module),
) : PyLspToolDescriptor(module, RuffPyTool.getInstance(), servedModules) {
  override val toolConfig: RuffSettings
    get() = project.service<RuffConfiguration>()

  override fun lspArguments(): List<String> {
    return listOf("server")
  }

  /** The [RuffFolderFallback] that the server got when it started, in a holder. `null` until the server starts. */
  @Volatile
  private var startedWith: StartedWith? = null

  private class StartedWith(val fallback: RuffFolderFallback?)

  /** Whether the folders of this server can get the project config. The modules of one server share a workspace. */
  private val getsProjectConfig: Boolean = runReadActionBlocking { ruffGetsProjectConfig(module, project) }

  private fun computeFallback(): RuffFolderFallback? =
    if (getsProjectConfig) ruffFolderFallback(roots.toList(), project.guessProjectDir()) else null

  private fun fallbackOfThisStart(): RuffFolderFallback? =
    (startedWith ?: StartedWith(computeFallback()).also { startedWith = it }).fallback

  /** Whether the project config of the workspace folders differs from the one that the server got when it started. */
  internal fun fallbackIsStale(): Boolean {
    val started = startedWith ?: return false
    return started.fallback != computeFallback()
  }

  /**
   * Starts the server in the directory of the project config, when a workspace folder gets it.
   *
   * The server resolves the relative paths of a `configuration` file against its working directory. For a config
   * that Ruff finds itself, it resolves them against the directory of that config. The two must agree.
   */
  override suspend fun resolveCommandLine(): GeneralCommandLine {
    val fallback = computeFallback()
    startedWith = StartedWith(fallback)
    val commandLine = super.resolveCommandLine()
    val configDir = fallback?.config?.file?.parent ?: return commandLine
    return configDir.fileSystem.getNioPath(configDir)?.let { commandLine.withWorkingDirectory(it) } ?: commandLine
  }

  /** Gives each workspace folder without a Ruff config the project config, see [ruffFallbackConfig]. */
  override fun createInitializationOptions(): Map<String, Any>? {
    val fallback = fallbackOfThisStart() ?: return null
    val configPath = getFilePath(fallback.config.file)
    // The server expands the environment variables in the path, so it would read another file.
    if ('$' in configPath) {
      thisLogger().info("Ruff cannot read the project config at '$configPath'. The server does not get it.")
      return null
    }
    return ruffInitializationOptions(fallback, configPath, ::getFileUri)
  }

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    // `Reformat Code` is delegated to the Ruff LSP server (`textDocument/formatting`), which keeps the long-lived
    // server in charge and avoids a per-format process spawn. The combined "sort imports when formatting" case cannot
    // be expressed as one LSP format request, because import sorting is a lint fix and not a part of `ruff format`.
    // RuffFormattingService takes that case with its own two-pass pipeline; see its KDoc. It is registered first and
    // claims only an explicit whole-file reformat, so a fragment selection still arrives here.
    override val formattingCustomizer = object : LspFormattingSupport() {
      override fun shouldFormatThisFileExclusivelyByServer(
        file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean,
      ): Boolean {
        return this@RuffLspClientDescriptor.toolConfig.formatting
      }
    }

    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun customizeQuickFixes(diagnostic: Diagnostic, quickFixes: List<IntentionAction>): List<IntentionAction> {
        val disableQuickFix = if (quickFixes.count {
            // there are always 8, we need to count the filled ones
            it.text.isNotEmpty()
          } > 1) quickFixes.firstOrNull { ": Disable for this line" in it.text }?.let {
          object : PyLspAction(diagnostic, it) {
            override fun getText(): @IntentionName String {
              return RuffBundle.message("intention.name.disable.for.this.line")
            }
          }
        }
        else null
        return quickFixes.mapNotNull { quickfix ->
          if (quickfix === disableQuickFix?.action) null
          else object : PyLspAction(diagnostic, quickfix) {
            override fun getText(): @IntentionName String {
              return quickFixMessage(quickfix.text)
            }

            override fun getOptions(): List<IntentionAction> =
              quickFixOptions(diagnostic).let {
                if (disableQuickFix != null) {
                  listOf(disableQuickFix) + it
                }
                else it
              }
          }
        }
      }
    }

    override fun codeCustomizer(@Nls code: String): String =
      project.service<RuffService>().ruleInformation[code]?.name ?: code

    override fun quickFixMessage(text: String): @IntentionName String {
      @Suppress("HardCodedStringLiteral")
      return Regex("""(?<=Ruff \()(\w+)""").replace(text) { codeCustomizer(it.groupValues[1]) }
    }

    override fun quickFixOptions(diagnostic: Diagnostic): List<IntentionAction> {
      val code = diagnostic.code?.get()?.toString() ?: return emptyList()
      return listOf(
        RuffDisableRuleIntentionAction(code),
        RuffDisableRuleForFileIntentionAction(code),
      )
    }
  }

  override val commandDescriptions: Map<String, String?> = mapOf(
    "ruff.applyFormat" to "Format document",
    "ruff.applyAutofix" to null,
    "ruff.applyOrganizeImports" to null,
    "ruff.printDebugInformation" to "Print debug information",
  )

  override val lspServerListener: PyLspToolDescriptorLspServerListener = object : PyLspToolDescriptorLspServerListener() {
    override fun serverInitialized(params: InitializeResult) {
      super.serverInitialized(params)
      val service = project.service<RuffService>()
      // The server reports which Ruff it is, so the cache can tell a sibling module's server apart
      // from a Ruff that was replaced, without spending a process to find out.
      val version = params.serverInfo?.version
      val module = this@RuffLspClientDescriptor.module
      project.service<PyLspService>().cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
        service.gatherInformation(module, version)
      }
    }
  }
}
