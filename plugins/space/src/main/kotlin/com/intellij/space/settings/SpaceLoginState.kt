package com.intellij.space.settings

import circlet.workspaces.Workspace
import libraries.coroutines.extra.LifetimeSource

sealed class SpaceLoginState(val server: String) {
  class Disconnected(server: String, val error: String? = null) : SpaceLoginState(server)
  class Connected(server: String, val workspace: Workspace) : SpaceLoginState(server)
  class Connecting(server: String, val lt: LifetimeSource) : SpaceLoginState(server)
}
