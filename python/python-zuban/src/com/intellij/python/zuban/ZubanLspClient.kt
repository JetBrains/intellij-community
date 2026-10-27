// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.zuban

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.python.lsp.core.PyLspService
import com.intellij.python.lsp.core.PyLspTool
import com.intellij.python.lsp.core.PyLspToolCustomization
import com.intellij.python.lsp.core.PyLspToolDescriptor
import com.intellij.python.lsp.core.PyLspToolIntegrationProvider
import com.intellij.python.lsp.core.PyLspToolSettings
import com.intellij.python.lsp.core.isUsable
import com.intellij.python.lsp.core.pyLspAttachedDescriptor
import com.intellij.python.lsp.core.pyLspInterpreterOf
import com.intellij.python.lsp.core.pyLspProjectsToServeWith
import com.intellij.python.lsp.core.pyServedProjects
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUtils
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.zuban.common.ZubanTypeCheckingMode
import com.intellij.util.EnvironmentUtil
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.project.PyProject
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.eclipse.lsp4j.Diagnostic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

/** The variable zuban reads extra search paths from, after `PYTHONPATH`. */
private const val MYPYPATH = "MYPYPATH"

/**
 * Runs one zuban server for the modules of a workspace that share an interpreter.
 *
 * Zuban takes one interpreter from its initialization options and uses it for every workspace folder,
 * so modules with different interpreters need servers of their own, see
 * [ZubanPyTool.serverNeedsOneInterpreter]. Zuban also reads its configuration from the first folder
 * only. The modules of one server therefore share the configuration of the lowest content root, as they
 * would with `mypy` run there. Zuban ignores `workspace/didChangeWorkspaceFolders`, so a new folder set
 * needs a new server, which the base provider starts.
 */
class ZubanLspIntegrationProvider : PyLspToolIntegrationProvider() {
  override fun getDescriptor(pyProject: PyProject): PyLspToolDescriptor {
    val served = pyLspProjectsToServeWith(pyProject, zubanPyTool())
    val descriptor = ZubanLspClientDescriptor(served.first(), served)
    // A descriptor that `fileOpened` did not build must still reach this provider, see `attach`.
    pyLspAttachedDescriptor(descriptor, ZubanLspIntegrationProvider::class.java)
    return descriptor
  }

  override fun pyTool(project: Project): PyLspTool<*> = zubanPyTool()

  override val servesEveryModule: Boolean get() = true

  override fun subscribeOnChanges(pyTool: PyLspTool<*>, project: Project, parentDisposable: Disposable) {
    super.subscribeOnChanges(pyTool, project, parentDisposable)
    val connection = project.messageBus.connect(parentDisposable)
    connection.subscribe(ModuleRootListener.TOPIC, ZubanSourceRootListener(project))
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, ZubanPackageListener(project))
  }
}

/**
 * Restarts the zuban servers when the source roots of a served module change.
 *
 * The source roots reach zuban through [MYPYPATH], which a running process cannot read again.
 */
private class ZubanSourceRootListener(private val project: Project) : ModuleRootListener {
  override fun rootsChanged(event: ModuleRootEvent) {
    val manager = LspClientManager.getInstance(project)
    if (manager.getClients(ZubanLspIntegrationProvider::class.java).isEmpty()) return
    // `rootsChanged` runs inside a write action, and the roots are read after it.
    project.service<PyLspService>().cs.launch {
      val changed = readAction {
        manager.getClients(ZubanLspIntegrationProvider::class.java)
          .mapNotNull { it.descriptor as? ZubanLspClientDescriptor }
          .filter { it.refreshModuleRoots() }
      }
      if (changed.isEmpty()) return@launch
      project.service<PyLspService>().restartMutex.withLock {
        thisLogger().info("The source roots of a zuban module changed. Restarting the zuban servers.")
        manager.stopAndRestartClientsIfNeeded(ZubanLspIntegrationProvider::class.java)
      }
    }
  }
}

/**
 * Asks the zuban servers of an interpreter for their results again after a package change there.
 *
 * Zuban watches the site-packages itself, so a new package, for example a stub package that a quick fix
 * installed, reaches the server without a restart. The IDE keeps each pulled result until the file
 * changes, so without this the old errors stay on screen.
 */
private class ZubanPackageListener(private val project: Project) : PythonPackageManagementListener {
  override fun packagesChanged(interpreter: PythonInterpreter) {
    val clients = LspClientManager.getInstance(project).getClients(ZubanLspIntegrationProvider::class.java)
    if (clients.isEmpty()) return
    project.service<PyLspService>().cs.launch {
      for (client in clients) {
        if (client.isUsable && client.pyServedProjects.any { !it.residesOnModule.isDisposed && pyLspInterpreterOf(it) == interpreter }) {
          client.invalidateServerResults()
        }
      }
    }
  }
}

/**
 * The descriptor of one zuban server, see [ZubanLspIntegrationProvider]. All [servedProjects] share the
 * interpreter of [pyProject], which goes to zuban as `pythonExecutable`.
 *
 * The excluded roots do not go to zuban, so `usesExcludedRoots` stays `false`. Zuban reads excludes only
 * from the `exclude` setting of its configuration file. No initialization option and no environment
 * variable carries them. Today zuban checks only the open files, and the IDE never opens an excluded
 * file with a server, so this has no effect. With workspace diagnostics, see
 * [zubanInitializationOptions], zuban would also check the excluded directories.
 */
class ZubanLspClientDescriptor(
  pyProject: PyProject,
  servedProjects: List<PyProject> = listOf(pyProject),
) : PyLspToolDescriptor(pyProject, zubanPyTool(), servedProjects) {
  override val toolConfig: PyLspToolSettings
    get() = project.service<ZubanConfiguration>()

  override fun lspArguments(): List<String> = listOf("server")

  override val usesSourceRoots: Boolean get() = true

  /** The `pythonExecutable` of the initialization options, as [resolveCommandLine] last read it. */
  @Volatile
  private var pythonExecutable: String? = null

  override suspend fun resolveCommandLine(): GeneralCommandLine {
    val commandLine = super.resolveCommandLine()
    @Suppress("DEPRECATION") // The local check still reads the SDK.
    val interpreter = pyLspInterpreterOf(pyProject)?.takeIf { PyTypeEngineUtils.isLocalSdk(it.getSdkAPI()) }
    pythonExecutable = interpreter?.detectedEnvironment?.pythonBinaryPath?.let { zubanPythonExecutable(it) }
    val firstRoot = readAction {
      refreshModuleRoots()
      roots.firstOrNull()?.let { it.fileSystem.getNioPath(it) }
    }
    val mypyPath = firstRoot?.let { zubanMypyPath(it, sourceRoots().map(Path::of), EnvironmentUtil.getValue(MYPYPATH)) }
    if (mypyPath != null) commandLine.withEnvironment(MYPYPATH, mypyPath)
    return commandLine
  }

  /** The platform builds these options after the command line, so [pythonExecutable] is known here. */
  override fun createInitializationOptions(): Map<String, Any> =
    zubanInitializationOptions(pythonExecutable, project.service<ZubanConfiguration>().typeCheckingMode)

  override val lspCustomization: PyLspToolCustomization = object : PyLspToolCustomization(toolConfig, pyTool, project) {
    override val diagnosticsSupport: PyLspToolDiagnosticsSupport = object : PyLspToolDiagnosticsSupport() {
      override fun createAnnotation(
        holder: AnnotationHolder,
        diagnostic: Diagnostic,
        textRange: TextRange,
        quickFixes: List<IntentionAction>,
      ) {
        val file = holder.currentAnnotationSession.file
        super.createAnnotation(holder, diagnostic, textRange, zubanQuickFixes(file, diagnostic, textRange) + quickFixes)
      }
    }
  }
}

/**
 * The initialization options for a zuban server.
 *
 * Zuban does not find a virtual environment outside its workspace folders by itself, and it ignores the
 * interpreter its own binary lives in. So the options always name the interpreter of the module when
 * there is a local one. The [typeCheckingMode] goes out only when the user chose one, see
 * [ZubanTypeCheckingMode]. The other options keep their defaults:
 * - `diagnosticMode` stays `open-files-only`. With `workspace`, zuban answers `workspace/diagnostic`
 *   for the whole project, but the IDE does not send that request yet
 *   ([IJPL-189566](https://youtrack.jetbrains.com/issue/IJPL-189566)). When it does, send `workspace`
 *   to show the errors of every file. See [ZubanLspClientDescriptor] about the excluded roots then.
 */
@ApiStatus.Internal
@VisibleForTesting
fun zubanInitializationOptions(pythonExecutable: String?, typeCheckingMode: ZubanTypeCheckingMode): Map<String, Any> = buildMap {
  pythonExecutable?.let { put("pythonExecutable", it) }
  if (typeCheckingMode != ZubanTypeCheckingMode.AUTO) put("typeCheckingMode", typeCheckingMode.value)
}

/**
 * The `pythonExecutable` that makes zuban use the environment of [interpreter].
 *
 * This is a workaround for zuban. Zuban does not run or ask the
 * interpreter. `Settings::apply_python_executable` in its `config` crate takes the parent of the
 * directory of `pythonExecutable` as the environment prefix, and it reads only the path. The other
 * tools find the environment from the interpreter itself, so they need nothing like this.
 *
 * The rule fits a virtual environment, where the interpreter is in `bin` or `Scripts`. A Windows base
 * interpreter or a conda environment keeps `python.exe` in the prefix itself, so zuban would look one
 * level too high and find no packages. For such an interpreter the result names `Scripts\python.exe`
 * below the prefix. That file does not have to exist, because zuban never opens it. The workaround
 * depends on that behavior, so check it again when zuban changes how it reads `pythonExecutable`.
 */
@ApiStatus.Internal
@VisibleForTesting
fun zubanPythonExecutable(interpreter: Path, isWindows: Boolean = SystemInfo.isWindows): String {
  val directory = interpreter.parent ?: return interpreter.toString()
  if (!isWindows || directory.name.equals("Scripts", ignoreCase = true)) return interpreter.toString()
  return directory.resolve("Scripts").resolve(interpreter.name).toString()
}

/**
 * The `MYPYPATH` that gives zuban the source roots of a module, or `null` when there is nothing to add.
 *
 * Zuban splits the value at `:` on every OS, so a Windows path with a drive letter would break in two.
 * Each source root therefore goes out relative to [firstRoot], against which zuban resolves a relative
 * entry. A root that has no relative form, for example on another drive, is left out, and so is
 * [firstRoot] itself, because zuban searches its workspace folders anyway. The [inherited] value of the
 * IDE environment comes last, so the project roots win.
 */
@ApiStatus.Internal
@VisibleForTesting
fun zubanMypyPath(firstRoot: Path, sourceRoots: List<Path>, inherited: String?): String? {
  val entries = sourceRoots.mapNotNull { root -> relativeEntry(firstRoot, root) }.distinct()
  return (entries + listOfNotNull(inherited?.takeIf { it.isNotBlank() })).ifEmpty { null }?.joinToString(":")
}

private fun relativeEntry(firstRoot: Path, root: Path): String? {
  val relative = try {
    firstRoot.relativize(root).normalize()
  }
  catch (_: IllegalArgumentException) {
    return null
  }
  return relative.invariantSeparatorsPathString.takeIf { it.isNotEmpty() && ':' !in it }
}
