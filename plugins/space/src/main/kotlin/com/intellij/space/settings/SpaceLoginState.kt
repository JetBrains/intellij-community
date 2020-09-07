package com.intellij.space.settings

import circlet.workspaces.Workspace
import com.intellij.openapi.util.NlsContexts.DetailedDescription

sealed class SpaceLoginState(val server: String) {
  class Disconnected(server: String, @DetailedDescription val error: String? = null) : SpaceLoginState(server)
  class Connected(server: String, val workspace: Workspace) : SpaceLoginState(server)
  class Connecting(server: String, private val cancelationHandler: () -> Unit) : SpaceLoginState(server) {
    fun cancel() = cancelationHandler()
  }
}
