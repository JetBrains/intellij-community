package com.intellij.terminal.backend.hyperlinks.rpc

import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.project.findProject
import com.intellij.terminal.backend.hyperlinks.BackendTerminalHyperlinksSessionsManager
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalCreateHyperlinksSessionRequest
import org.jetbrains.plugins.terminal.hyperlinks.rpc.TerminalHyperlinksRemoteApi
import org.jetbrains.plugins.terminal.hyperlinks.session.TerminalHyperlinksSessionId

internal class TerminalHyperlinksRemoteApiImpl : TerminalHyperlinksRemoteApi {
  override suspend fun createNewSession(request: TerminalCreateHyperlinksSessionRequest): TerminalHyperlinksSessionId {
    val project = request.projectId.findProject()
    // Provided descriptor can be null only in the case of RemDev backend.
    // In this case we consider that the process is running in the environment of the backend project.
    val eelDescriptor = request.eelDescriptor ?: project.getEelDescriptor()

    return BackendTerminalHyperlinksSessionsManager.getInstance().createNewSession(project, eelDescriptor).id
  }

  override suspend fun closeSession(sessionId: TerminalHyperlinksSessionId) {
    return BackendTerminalHyperlinksSessionsManager.getInstance().closeSession(sessionId)
  }
}