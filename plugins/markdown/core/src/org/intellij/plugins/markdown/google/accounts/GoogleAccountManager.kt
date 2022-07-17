// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.fasterxml.jackson.databind.DeserializationFeature
import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserInfo
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.jacksonMapper

/**
 * Handles application-level Google accounts
 */
@Service
internal class GoogleAccountManager : AccountManagerBase<GoogleAccount, GoogleCredentials>(SERVICE_DISPLAY_NAME) {
  companion object {
    fun createAccount(userInfo: GoogleUserInfo) = GoogleAccount(userInfo.id, userInfo.email)

    const val SERVICE_DISPLAY_NAME: String = "Google Accounts"
  }

  override fun accountsRepository(): AccountsRepository<GoogleAccount> = service<GooglePersistentAccounts>()

  override fun serializeCredentials(credentials: GoogleCredentials): String =
    jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(credentials)

  override fun deserializeCredentials(credentials: String): GoogleCredentials =
    jacksonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(credentials, GoogleCredentials::class.java)
}
