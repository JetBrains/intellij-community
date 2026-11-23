// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * The application-wide actions that run the commands of the Python LSP tool servers.
 *
 * The [ActionManager] holds one action for each ID, and every server of one tool advertises the same commands. So the
 * servers of all open projects share one action for each command. The action keeps no project. It runs the command on
 * a server in the project of the event, see [PyLspCommandAction].
 *
 * Each server start is an owner of the IDs that it registered. An action stays while it has an owner.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class PyLspCommandActions {
  private class Holder(var text: @NlsActions.ActionText String, var action: AnAction) {
    val owners: MutableSet<Any> = HashSet()
  }

  /** The actions that this registry put into the [ActionManager], by ID. Guarded by itself. */
  private val holders = HashMap<String, Holder>()

  /**
   * Makes [owner] an owner of [actionId] with the action that [createAction] gives for [text].
   *
   * The first owner registers the action. A later owner with another [text] replaces it, so the action shows the text
   * of the last server start. A restart or an upgrade can change the server name. An action that another caller
   * removed from the [ActionManager] gets registered again.
   */
  fun acquire(owner: Any, actionId: String, text: @NlsActions.ActionText String, createAction: (String) -> AnAction) {
    synchronized(holders) {
      val holder = holders[actionId]
      val action = if (holder == null || holder.text != text) createAction(text) else holder.action
      register(actionId, action)
      // The holder changes only after the registration, so a failed registration leaves no holder without an owner.
      (holder ?: Holder(text, action).also { holders[actionId] = it }).apply {
        this.text = text
        this.action = action
        owners.add(owner)
      }
    }
  }

  /**
   * Removes an action under [actionId] that this registry does not hold. A server that hides a command must not leave
   * an action of another caller under its ID, see PY-86023. An action that another owner holds stays.
   */
  fun removeForeign(actionId: String) {
    synchronized(holders) {
      if (actionId in holders) return
      val actionManager = ActionManager.getInstance()
      if (actionManager.getAction(actionId) != null) actionManager.unregisterAction(actionId)
    }
  }

  /** Puts [action] under [actionId]. An ID that this registry does not hold can still be registered, see PY-86023. */
  private fun register(actionId: String, action: AnAction) {
    val actionManager = ActionManager.getInstance()
    val registered = actionManager.getAction(actionId)
    if (registered === action) return
    // A second registration of one ID throws.
    if (registered != null) actionManager.unregisterAction(actionId)
    actionManager.registerAction(actionId, action)
  }

  /** Removes [owner] from the owners of [actionId]. The last owner unregisters the action. */
  fun release(owner: Any, actionId: String) {
    synchronized(holders) {
      val holder = holders[actionId] ?: return
      if (!holder.owners.remove(owner) || holder.owners.isNotEmpty()) return
      holders.remove(actionId)
      val actionManager = ActionManager.getInstance()
      if (actionManager.getAction(actionId) === holder.action) actionManager.unregisterAction(actionId)
    }
  }

  /** Unregisters every action and forgets every owner, so a failed test leaves no action to the next one. */
  @TestOnly
  fun releaseAll() {
    synchronized(holders) {
      val actionManager = ActionManager.getInstance()
      for ((actionId, holder) in holders) {
        if (actionManager.getAction(actionId) === holder.action) actionManager.unregisterAction(actionId)
      }
      holders.clear()
    }
  }

  companion object {
    fun getInstance(): PyLspCommandActions = service()
  }
}

/**
 * Runs [command] on a server of [providerClass] in the project of the event.
 *
 * Every project shares the action, see [PyLspCommandActions]. So the action must not keep the project of the server
 * that registered it. [clientsOf] gives the clients of the tool in a project.
 *
 * A command that [takesDocument] acts on the document of the editor. It goes to the client that serves the file of
 * that document, with the document as a [VersionedTextDocumentIdentifier]. The action is disabled when no such client
 * runs, see [PyLspToolDescriptor.documentCommands].
 */
@ApiStatus.Internal
class PyLspCommandAction(
  text: @NlsActions.ActionText String,
  private val providerClass: Class<out LspIntegrationProvider>,
  private val command: String,
  private val takesDocument: Boolean = false,
  private val clientsOf: (Project) -> Collection<LspClient> = { LspClientManager.getInstance(it).getClients(providerClass) },
) : AnAction(text) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project?.takeUnless { it.isDisposed }
    val visible = project != null && clientFor(project, e.getData(CommonDataKeys.VIRTUAL_FILE)) != null
    e.presentation.isVisible = visible
    e.presentation.isEnabled = visible && (!takesDocument || documentTarget(project, e.getData(CommonDataKeys.EDITOR)?.document) != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project?.takeUnless { it.isDisposed } ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val document = e.getData(CommonDataKeys.EDITOR)?.document
    project.service<PyLspService>().cs.launch {
      val (client, arguments) = readAction<Pair<LspClient, List<Any>?>?> {
        if (takesDocument) documentTarget(project, document)?.let { (client, identifier) -> client to listOf(identifier) }
        else clientFor(project, file)?.let { it to null }
      } ?: return@launch
      try {
        client.sendRequest { it.workspaceService.executeCommand(ExecuteCommandParams(command, arguments)) }
      }
      catch (error: ResponseErrorException) {
        logger<PyLspCommandAction>().warn("${providerClass.simpleName} failed to run '$command'", error)
      }
    }
  }

  @RequiresReadLock
  private fun clientFor(project: Project, file: VirtualFile?): LspClient? {
    if (project.isDisposed) return null
    return pyLspCommandClient(clientsOf(project), command, file)
  }

  /** The client that serves the file of [document], and the identifier of [document] for that client. */
  @RequiresReadLock
  private fun documentTarget(project: Project, document: Document?): Pair<LspClient, VersionedTextDocumentIdentifier>? {
    if (document == null || project.isDisposed) return null
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    val client = pyLspCommandClients(clientsOf(project), command).firstOrNull { it.descriptor.isSupportedFile(file) } ?: return null
    return client to VersionedTextDocumentIdentifier(client.getDocumentIdentifier(file).uri, client.getDocumentVersion(document))
  }
}

/**
 * The client of [clients] that runs [command]. Only a running server that advertises [command] counts. The client
 * that serves [file] comes first. A tool with one server for each module must run the command for the module of the
 * file.
 */
@ApiStatus.Internal
@RequiresReadLock
fun pyLspCommandClient(clients: Collection<LspClient>, command: String, file: VirtualFile?): LspClient? {
  val candidates = pyLspCommandClients(clients, command)
  if (file != null) {
    candidates.firstOrNull { (it.descriptor as? PyLspToolDescriptor)?.servedProjectOf(file) != null }?.let { return it }
  }
  return candidates.firstOrNull()
}

/** The clients of [clients] whose server runs and advertises [command]. */
@RequiresReadLock
private fun pyLspCommandClients(clients: Collection<LspClient>, command: String): List<LspClient> =
  clients.filter { it.isUsable && command in it.initializeResult?.capabilities?.executeCommandProvider?.commands.orEmpty() }
