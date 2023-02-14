// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount

@State(name = "GoogleAccounts", storages = [Storage(value = "google.xml")], reportStatistic = false)
internal class GooglePersistentAccounts
  : AccountsRepository<GoogleAccount>,
    SimplePersistentStateComponent<GooglePersistentAccounts.GoogleAccountsState>(GoogleAccountsState()) {

  override var accounts: Set<GoogleAccount>
    get() = state.accounts.toSet()
    set(value) {
      state.accounts = value.toMutableList()
    }

  class GoogleAccountsState : BaseState() {
    var accounts by list<GoogleAccount>()
  }
}
