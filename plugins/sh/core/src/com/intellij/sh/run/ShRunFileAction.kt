// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.psi.PsiFile
import com.intellij.sh.parser.ShShebangParserUtil
import com.intellij.sh.psi.ShFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Runs the current shell file directly in the Terminal tool window. No run configuration is created or consulted; the
 * command is built by [ShTerminalCommandBuilder] and handed to [ShRunner], the same path the run configuration's
 * "Execute in the terminal" option takes.
 *
 * The action lives in the embedded core module, so it is registered in every product mode. In remote development it is
 * performed on the backend ([ActionRemoteBehaviorSpecification.BackendOnly]): the gutter click already runs there, and
 * the interpreter, the [ShRunnerAdditionalCondition] exclusions and the default shell belong to the host. [ShRunner]
 * then delivers the command to the frontend that owns the terminal.
 */
internal class ShRunFileAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.BackendOnly {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabled(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
    val virtualFile = psiFile.virtualFile ?: return
    val runner = ApplicationManager.getApplication().getService(ShRunner::class.java) ?: return
    if (!runner.isAvailable(project)) return

    // The terminal runs the file from disk, so unsaved edits must reach it first (the program runner did the same).
    FileDocumentManager.getInstance().saveAllDocuments()
    val shellFile = psiFile as? ShFile
    // The shebang needs the parsed file and the default shell lookup talks to the target environment: both leave the EDT.
    // The ClientId travels along so that the runner reaches the client that asked for the run.
    ShRunFileScope.getInstance(project).coroutineScope.launch(Dispatchers.IO + ClientId.current.asContextElement()) {
      val shebang = shellFile?.let { file -> readAction { if (file.isValid) ShShebangParserUtil.getShebangExecutable(file) else null } }
      val request = buildRequest(project, virtualFile, isShellFile = shellFile != null, shebang) ?: return@launch
      runner.run(project, request.command, request.workingDirectory, virtualFile.name, true)
    }
  }

  private class Request(val command: String, val workingDirectory: String)

  private fun buildRequest(project: Project, virtualFile: VirtualFile, isShellFile: Boolean, shebang: String?): Request? {
    val projectDescriptor = project.getEelDescriptor()
    val nioPath = virtualFile.toNioPathOrNull()
    val scriptPath = toEelPath(virtualFile, nioPath, projectDescriptor) ?: return null
    val (interpreter, interpreterOptions) = when {
      shebang != null -> ShShebangParserUtil.parseInterpreterAndOptions(shebang).let { it.first to it.second }
      isShellFile -> toTargetPath(ShConfigurationType.getDefaultShell(project)) to ""
      // A file of another language with a `#!` first line runs by its own shebang.
      else -> "" to ""
    }
    val command = ShTerminalCommandBuilder.scriptFileCommand(
      interpreter, interpreterOptions, scriptPath.toString(), "", emptyMap(), scriptPath.descriptor.osFamily,
    )
    // A directory inside the project's environment travels in that environment's spelling; a directory elsewhere
    // (a WSL script open in a local project) keeps the nio spelling the terminal can route directly. See ShRunner.run.
    val workingDirectory = when {
      scriptPath.descriptor == projectDescriptor -> scriptPath.parent?.toString()
      else -> nioPath?.parent?.toString()
    } ?: return null
    return Request(command, workingDirectory)
  }

  companion object {
    const val ID: String = "runShellFileAction"

    private val LOG = logger<ShRunFileAction>()

    private fun isEnabled(e: AnActionEvent): Boolean {
      if (e.project == null) return false
      val file = e.getData(CommonDataKeys.PSI_FILE) ?: return false
      if (ApplicationManager.getApplication().getService(ShRunner::class.java) == null) return false
      if (ShRunnerAdditionalCondition.EP.extensionsIfPointIsRegistered.any { it.isRunningProhibitedForFile(file) }) return false
      return file is ShFile || startsWithShebang(file)
    }

    /** Reads the document rather than the PSI tree, which is cheap and works for any text file; a binary file has no document. */
    private fun startsWithShebang(file: PsiFile): Boolean {
      val document = file.viewProvider.document ?: return false
      return document.immutableCharSequence.startsWith("#!")
    }

    /** The script in the spelling of the environment it lives in. */
    private fun toEelPath(virtualFile: VirtualFile, nioPath: Path?, projectDescriptor: EelDescriptor): EelPath? {
      return try {
        nioPath?.asEelPath() ?: EelPath.parse(virtualFile.path, projectDescriptor)
      }
      catch (e: EelPathException) {
        LOG.warn("Cannot map ${virtualFile.path} to its environment", e)
        null
      }
      catch (e: NoSuchElementException) {
        LOG.warn("Cannot map ${virtualFile.path} to its environment", e)
        null
      }
    }

    /** [ShConfigurationType.getDefaultShell] answers with a nio path; a routed one is converted to the target spelling. */
    private fun toTargetPath(path: String): String {
      if (path.isEmpty()) return path
      return try {
        Path.of(path).asEelPath().toString()
      }
      catch (e: InvalidPathException) {
        path
      }
      catch (e: EelPathException) {
        path
      }
      catch (e: NoSuchElementException) {
        path
      }
    }
  }
}

@Service(Service.Level.PROJECT)
internal class ShRunFileScope(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): ShRunFileScope = project.service()
  }
}
