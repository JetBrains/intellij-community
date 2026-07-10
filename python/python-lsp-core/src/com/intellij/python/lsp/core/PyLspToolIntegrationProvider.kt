// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.modcommand.ModCommandAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.buildChildren
import com.intellij.openapi.util.text.buildHtml
import com.intellij.openapi.util.text.plus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspIntegrationProvider.LspClientStarter
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.lsp.api.customization.LspCompletionCustomizer
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer
import com.intellij.platform.lsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.platform.lsp.api.customization.LspHoverSupport
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsCustomizer
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.python.community.execService.asGeneralCommandLine
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.getExecutableWithBaseArgs
import com.intellij.python.pytools.isEnabledOn
import com.intellij.python.pytools.lsp.PyLspTool
import com.intellij.python.pytools.lsp.PyLspToolSettings
import com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable
import com.intellij.python.pytools.ui.getInstalledToolPackage
import com.intellij.ui.JBColor
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.ModuleOrProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeResult
import org.jetbrains.annotations.Nls
import java.util.Collections
import javax.swing.Icon

abstract class PyLspToolIntegrationProvider : LspIntegrationProvider {
  private val listenerConnectedForProjects: MutableSet<Project> = Collections.synchronizedSet(HashSet<Project>())

  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspClientStarter) {
    // Find the module for this file
    val module = ModuleUtilCore.findModuleForFile(file, project) ?: return
    val moduleOrProject = ModuleOrProject.ModuleAndProject(module)

    val descriptor = getDescriptor(module)
    if (!descriptor.isSupportedFile(file))
      return
    if (!descriptor.pyTool.isEnabledOn(project))
      return
    descriptor.supportProvider = this
    if (listenerConnectedForProjects.add(project)) {
      val listenerDisposable = Disposer.newDisposable("Python LSP package listener")
      Disposer.register(project, listenerDisposable)
      Disposer.register(listenerDisposable) {
        listenerConnectedForProjects.remove(project)
      }
      subscribeOnChanges(descriptor.pyTool, project, listenerDisposable)
      // The set is app-level (the provider is an application extension); drop the project when it is
      // disposed so we don't retain disposed projects (caught by the test-framework leak checker).
      Disposer.register(PythonPluginDisposable.getInstance(project), Disposable { listenerConnectedForProjects.remove(project) })
    }

    runBlockingMaybeCancellable {
      descriptor.pyTool.getExecutableWithBaseArgs(moduleOrProject, descriptor.executableName)
    }.onFailure { return }

    clientStarter.ensureClientStarted(descriptor)
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem? {
    return object : LspClientWidgetItem(
      lspClient = lspClient,
      currentFile = currentFile,
      icon = getIcon(lspClient),
      settingsPageClass = PyExternalToolsConfigurable::class.java
    ) {
      override val itemLabel: @NlsSafe String
        get() = presentableName(lspClient) + versionPostfix + rootPostfix
    }
  }

  abstract fun getDescriptor(module: Module): PyLspToolDescriptor

  fun getIcon(lspClient: LspClient): Icon = (lspClient.descriptor as PyLspToolDescriptor).pyTool.icon

  fun presentableName(lspClient: LspClient): @NlsSafe String = lspClient.initializeResult?.serverInfo?.name
                                                               ?: lspClient.descriptor.presentableName

  protected open fun subscribeOnChanges(pyTool: PyTool, project: Project, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable)
      .subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, LspPackageListener(pyTool, project))
  }

  inner class LspPackageListener(val pyTool: PyTool, val project: Project) : PythonPackageManagementListener {
    var wasThisToolInstalled: Boolean? = null

    override fun packagesChanged(sdk: Sdk) {
      val lspServerManager = LspClientManager.getInstance(project)
      val manager = PythonPackageManager.forSdk(project, sdk)
      val isInstalled = manager.getInstalledToolPackage(pyTool) != null
      if (isInstalled != wasThisToolInstalled) {
        val providerClass = this@PyLspToolIntegrationProvider::class.java
        if (isInstalled) {
          lspServerManager.startClientsIfNeeded(providerClass)
        }
        else {
          lspServerManager.stopClients(providerClass)
        }
      }
      wasThisToolInstalled = isInstalled
    }
  }

}

/**
 * Base class for Python LSP tool descriptors that can be either module-scoped or project-scoped.
 *
 * When a [module] is provided, the LSP server will only serve files within that module's content roots.
 * When [module] is null, it falls back to project-wide behavior using all project base directories.
 */
abstract class PyLspToolDescriptor(
  val module: Module,
  val pyTool: PyLspTool<*>,
) : LspClientDescriptor(module.project, pyTool.presentableName, *ModuleRootManager.getInstance(module).contentRoots) {
  /**
   * Whether this python LSP tool can serve Jupyter notebooks. Defaults to `true` because all current Python LSP tools support notebooks.
   */
  open val supportsNotebooks: Boolean get() = true

  override fun isSupportedFile(file: VirtualFile): Boolean =
    isPythonFile(file, notebookSupported = supportsNotebooks) && isFileInModule(file)

  protected fun isFileInModule(file: VirtualFile): Boolean =
    ModuleUtilCore.findModuleForFile(file, project) == module

  abstract val toolConfig: PyLspToolSettings

  open val executableName: String get() = pyTool.packageName.name

  abstract fun lspArguments(): List<String>

  override fun createCommandLine(): GeneralCommandLine {
    val moduleOrProject = ModuleOrProject.ModuleAndProject(module)
    val executable = runBlockingMaybeCancellable {
      pyTool.getExecutableWithBaseArgs(moduleOrProject, executableName)
    }
    val (binary, baseArgs) = executable.getOr { throw ExecutionException(it.error.message) }
    val cmd = binary.asGeneralCommandLine().getOr { throw ExecutionException(it.error.message) }
      .withParameters(*baseArgs.toTypedArray(), *lspArguments().toTypedArray())
    return cmd
  }

  lateinit var supportProvider: PyLspToolIntegrationProvider

  private val registeredActionIds = mutableListOf<String>()

  override val lspServerListener: PyLspToolDescriptorLspServerListener = PyLspToolDescriptorLspServerListener()

  open inner class PyLspToolDescriptorLspServerListener : LspServerListener {
    override fun serverInitialized(params: InitializeResult) {
      val actionManager = ActionManager.getInstance()
      val commandProvider = params.capabilities.executeCommandProvider

      if (commandProvider == null) return
      // workaround for IJPL-196574
      commandProvider.commands.forEach { command ->
        val actionId = "LSP.Command.$presentableName.$command"
        if (actionManager.getAction(actionId) != null) {
          // workaround for PY-86023
          actionManager.unregisterAction(actionId)
        }
        if (command in commandDescriptions && commandDescriptions[command] == null) return@forEach
        val text = "${params.serverInfo.name}: " + (commandDescriptions[command] ?: command)
        val action = object : AnAction(text) {
          override fun actionPerformed(e: AnActionEvent) {
            val lspServerManager = LspClientManager.getInstance(project)

            lspServerManager
              .getClients(supportProvider::class.java)
              .firstOrNull()
              ?.let { server ->
                project.service<PyLspService>().cs.launch {
                  server.sendRequest {
                    it.workspaceService.executeCommand(ExecuteCommandParams(command, null))
                  }
                }
              }
          }
        }
        actionManager.registerAction(actionId, action)
        registeredActionIds.add(actionId)
      }
    }

    override fun serverStopped(shutdownNormally: Boolean) {
      val actionManager = ActionManager.getInstance()
      registeredActionIds.forEach { actionId ->
        actionManager.unregisterAction(actionId)
      }
      registeredActionIds.clear()
    }
  }

  // workaround for IJPL-196574
  open val commandDescriptions: Map<String, String?> = emptyMap()

  override val lspCustomization: PyLspToolCustomization = PyLspToolCustomization(toolConfig, pyTool, project)
}

open class PyLspToolCustomization(
  val toolConfig: PyLspToolSettings,
  private val pyTool: PyTool,
  private val project: Project,
) : LspCustomization() {
  val Diagnostic.presentableCode: String?
    get() = code?.get()?.toString()?.let { codeCustomizer(it) }

  open fun quickFixMessage(text: @IntentionName String): @IntentionName String = text

  open fun quickFixOptions(diagnostic: Diagnostic): List<IntentionAction> = emptyList()

  override val completionCustomizer: LspCompletionCustomizer = object : LspCompletionSupport() {
    override fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean =
      pyTool.isEnabledOn(project) && toolConfig.completions == true
  }

  protected open val diagnosticsSupport: PyLspToolDiagnosticsSupport = PyLspToolDiagnosticsSupport()

  // instead of using `shouldAskServerForDiagnostics` we also want to avoid `publishDiagnostics`
  final override val diagnosticsCustomizer: LspDiagnosticsCustomizer
    get() = if (pyTool.isEnabledOn(project) && toolConfig.inspections) diagnosticsSupport else LspDiagnosticsDisabled

  override val optimizeImportsCustomizer: LspOptimizeImportsCustomizer = LspOptimizeImportsDisabled

  override val inlayHintCustomizer: LspInlayHintSupport = object : LspInlayHintSupport() {
    override fun shouldAskServerForInlayHints(file: VirtualFile): Boolean =
      pyTool.isEnabledOn(project) && toolConfig.inlayHints == true
  }

  override val hoverCustomizer: LspHoverCustomizer
    get() = if (pyTool.isEnabledOn(project) && toolConfig.documentation == true) LspHoverSupport() else LspHoverDisabled

  override val codeLensCustomizer: LspCodeLensCustomizer = LspCodeLensDisabled

  open inner class PyLspToolDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
      val messageLines = super.getMessage(diagnostic).split("\n")
      // source is nullable
      val firstLine =
        messageLines[0] + "  ${diagnostic.source.orEmpty()}" + diagnostic.presentableCode?.let { "($it)" }.orEmpty() // NON-NLS
      if (messageLines.size == 1) return firstLine
      return firstLine + "\n" + messageLines.drop(1).joinToString("\n", prefix = "\n") // NON-NLS
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
      val code = diagnostic.presentableCode
      val source = diagnostic.source
      val style = "color: gray"
      // workaround for IJPL-196845
      val codeLink = when {
        diagnostic.codeDescription?.href != null && code != null -> HtmlChunk.link(diagnostic.codeDescription?.href!!, code)
        code != null -> HtmlChunk.span(style).buildChildren { append(HtmlChunk.text(code)) }
        else -> null
      }
      val suffix = when {
        source != null && codeLink != null && diagnostic.codeDescription?.href != null -> {
          // "source(code)" with link
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text("$source("))
          } +
          codeLink +
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text(")"))
          }
        }
        source != null && code != null -> {
          // "source(code)" without link
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text("$source($code)"))
          }
        }
        source != null -> {
          // "source"
          HtmlChunk.span(style).buildChildren {
            append(HtmlChunk.text(source))
          }
        }
        else -> {
          // "code" or null (empty)
          codeLink
        }
      }

      val tooltip = super.getTooltip(diagnostic)
      // workaround for leading spaces getting removed
      val fixed = tooltip.replace(Regex("\n +")) { m ->
        val spaces = m.value.substring(1)
        "\n" + "\u00A0".repeat(spaces.length)
      }
      // workaround for IJPL-196664
      return buildHtml {
        append(HtmlChunk.text(fixed))
        if (suffix != null) {
          append(HtmlChunk.nbsp() + suffix)
        }
      }
    }

    // workaround for IJPL-196573
    override fun getHighlightSeverity(diagnostic: Diagnostic): HighlightSeverity? =
      when (diagnostic.severity) {
        DiagnosticSeverity.Hint -> HighlightSeverity.INFORMATION
        else -> super.getHighlightSeverity(diagnostic)
      }

    // workaround for IJPL-196573
    override fun getEnforcedTextAttributes(diagnostic: Diagnostic): TextAttributes? =
      when (diagnostic.severity) {
        DiagnosticSeverity.Information -> TextAttributes().apply {
          effectType = EffectType.WAVE_UNDERSCORE
          effectColor = JBColor.BLUE
        }
        DiagnosticSeverity.Hint -> TextAttributes().apply {
          effectType = EffectType.BOLD_DOTTED_LINE
          effectColor = JBColor.GRAY
        }
        else -> null
      }
  }

  open inner class PyLspAction(val diagnostic: Diagnostic, val action: IntentionAction) : IntentionAction by action,
                                                                                          CustomizableIntentionAction,
                                                                                          IntentionActionWithOptions {
    override fun getText(): @IntentionName String {
      return quickFixMessage(action.text)
    }

    override fun isShowSubmenu(): Boolean {
      return true
    }

    override fun getOptions(): List<IntentionAction> {
      return quickFixOptions(diagnostic)
    }

    override fun asIntention(): IntentionAction {
      return this
    }

    override fun getCombiningPolicy(): IntentionActionWithOptions.CombiningPolicy {
      // ideally this would be `InspectionOptionsOnly`, but at this time it not investigated
      return IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly
    }

    //<editor-fold defaultstate="collapsed" desc="default method explicit delegates">
    override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo {
      return action.generatePreview(project, editor, psiFile)
    }

    override fun asModCommandAction(): ModCommandAction? {
      return action.asModCommandAction()
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
      return action.getElementToMakeWritable(currentFile)
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
      return action.getFileModifierForPreview(target)
    }

    override fun isDumbAware(): Boolean {
      return action.isDumbAware
    }
    //</editor-fold>
  }

  open fun codeCustomizer(@Nls code: String): @Nls String = code
}

@Service(Service.Level.PROJECT)
class PyLspService(val cs: CoroutineScope)