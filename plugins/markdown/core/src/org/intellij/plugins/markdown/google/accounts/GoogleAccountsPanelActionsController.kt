// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.awt.RelativePoint
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

class GoogleAccountsPanelActionsController(private val model: GoogleAccountsListModel)
  : AccountsPanelActionsController<GoogleAccount> {

  companion object {
    private val LOG = logger<GoogleAccountsPanelActionsController>()
  }

  private val userInfoService get() = service<GoogleUserInfoService>()

  override val isAddActionWithPopup: Boolean = false

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    AcquireUserInfoTask(parentComponent) { userCred, userInfo ->
      if (isAccountUnique(userInfo.id)) {
        val account = GoogleAccountManager.createAccount(userInfo)
        model.add(account, userCred)
      }
    }.queue()
  }

  override fun editAccount(parentComponent: JComponent, account: GoogleAccount) {
    AcquireUserInfoTask(parentComponent) { userCred, userInfo ->
      account.name = userInfo.email
      model.update(account, userCred)
    }.queue()
  }

  private fun isAccountUnique(accountId: String): Boolean = model.accounts.none { it.id == accountId }

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

    private lateinit var credentialsFuture: CompletableFuture<GoogleCredentials>

    private lateinit var credentials: GoogleCredentials
    private lateinit var userInfo: GoogleUserInfo

    override fun run(indicator: ProgressIndicator) {
      val request = getGoogleAuthRequest()
      credentialsFuture = service<GoogleOAuthService>().authorize(request)
      credentials = ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture, indicator)

      userInfo = runBlockingCancellable {
        userInfoService.acquireUserInfo(credentials.accessToken)
      }
    }

    override fun onSuccess() {
      process(credentials, userInfo)
    }

    override fun onThrowable(error: Throwable) {
      if (error is GoogleAppCredentialsException) parentComponent?.let { showNetworkErrorMessage(it) }
      else LOG.info(error.localizedMessage)
    }

    override fun onCancel() {
      credentialsFuture.cancel(true)
    }
  }
}
