// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.ui.AccountsListModelBase
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.GoogleAppCredentialsException
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserInfo
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import org.intellij.plugins.markdown.google.authorization.getGoogleAuthRequest
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

class GoogleAccountsListModel : AccountsListModelBase<GoogleAccount, GoogleCredentials>() {

  companion object {
    private val LOG = logger<GoogleAccountsListModel>()
  }

  private val userInfoService get() = service<GoogleUserInfoService>()

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    AcquireUserInfoTask(parentComponent) { userCred, userInfo ->
      if (isAccountUnique(userInfo.id)) updateAccountList(userInfo, userCred)
    }.queue()
  }

  override fun editAccount(parentComponent: JComponent, account: GoogleAccount) {
    AcquireUserInfoTask(parentComponent) { userCred, userInfo ->
      newCredentials[account] = userCred
      account.name = userInfo.email
      notifyCredentialsChanged(account)
    }.queue()
  }

  private fun isAccountUnique(accountId: String): Boolean = accountsListModel.items.none { it.id == accountId }

  @RequiresEdt
  private fun updateAccountList(userInfo: GoogleUserInfo, credentials: GoogleCredentials) {
    val account = GoogleAccountManager.createAccount(userInfo)

    accountsListModel.add(account)
    newCredentials[account] = credentials

    notifyCredentialsChanged(account)
  }

  private fun showNetworkErrorMessage(parentComponent: JComponent) {
    MessageDialogBuilder.Message(
      title = MarkdownBundle.message("markdown.google.network.problems.title"),
      message = MarkdownBundle.message("markdown.google.login.network.problems.msg")
    ).buttons(Messages.getOkButton()).icon(UIUtil.getErrorIcon()).show(null, parentComponent)
  }

  private inner class AcquireUserInfoTask(
    parentComponent: JComponent,
    private val process: (userCred: GoogleCredentials, userInfo: GoogleUserInfo) -> Unit
  ) : Task.Modal(null, parentComponent, MarkdownBundle.message("markdown.google.account.login.progress.title"), true) {

    private var credentials: GoogleCredentials? = null
    private var userInfo: GoogleUserInfo? = null

    override fun run(indicator: ProgressIndicator) {
      var credentialsFuture: CompletableFuture<GoogleCredentials> = CompletableFuture()

      try {
        val request = getGoogleAuthRequest()
        credentialsFuture = service<GoogleOAuthService>().authorize(request)
        credentials = ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture, indicator)

        val userInfoFuture = credentials?.let { userInfoService.acquireUserInfo(it.accessToken, indicator) }
        userInfo = userInfoFuture?.let { ProgressIndicatorUtils.awaitWithCheckCanceled(it) }
      }
      catch (t: Throwable) {
        when (t) {
          is ProcessCanceledException -> LOG.info("The authorization process has been canceled")
          is GoogleAppCredentialsException -> parentComponent?.let { showNetworkErrorMessage(it) }
          else -> LOG.info(t.localizedMessage)
        }

        credentialsFuture.cancel(true)
      }
    }

    override fun onSuccess() {
      if (credentials != null && userInfo != null) {
        process(credentials!!, userInfo!!)
      }
    }
  }
}
