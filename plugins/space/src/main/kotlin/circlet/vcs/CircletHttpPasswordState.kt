package circlet.vcs

import circlet.client.api.*

sealed class CircletHttpPasswordState {
    object NotChecked : CircletHttpPasswordState()

    object NotSet : CircletHttpPasswordState()

    class Set(val vcsHostingPassword: VcsHostingPassword) : CircletHttpPasswordState()
}

sealed class CircletKeysState {
    object NotChecked : CircletKeysState()

    object NotSet : CircletKeysState()

    class Set(val sshKeys: List<SshKeyData>) : CircletKeysState()
}
