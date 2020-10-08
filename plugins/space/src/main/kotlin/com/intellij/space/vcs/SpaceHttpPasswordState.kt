// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
