package com.intellij.space.vcs

import circlet.client.api.SshKeyData
import circlet.client.api.td.VcsHostingPassword

sealed class SpaceHttpPasswordState {
  object NotChecked : SpaceHttpPasswordState()

  object NotSet : SpaceHttpPasswordState()

  class Set(val vcsHostingPassword: VcsHostingPassword) : SpaceHttpPasswordState()
}

sealed class SpaceKeysState {
  object NotChecked : SpaceKeysState()

  object NotSet : SpaceKeysState()

  class Set(val sshKeys: List<SshKeyData>) : SpaceKeysState()
}
