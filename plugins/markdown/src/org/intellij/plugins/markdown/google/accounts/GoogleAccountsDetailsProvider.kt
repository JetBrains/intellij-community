// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.auth.ui.LoadingAccountsDetailsProvider
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.GoogleAuthorizedUserException
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserDetailed
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserInfo
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.getOrUpdateUserCredentials
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO

internal class GoogleAccountsDetailsProvider(
  progressIndicatorsProvider: ProgressIndicatorsProvider,
  private val accountManager: GoogleAccountManager,
  private val accountsListModel: GoogleAccountsListModel,
  private val oAuthService: GoogleOAuthService,
  private val userInfoService: GoogleUserInfoService
) : LoadingAccountsDetailsProvider<GoogleAccount, GoogleUserDetailed>(progressIndicatorsProvider) {

  companion object {
    private val LOG = logger<GoogleAccountsDetailsProvider>()
  }

  @RequiresEdt
  override fun scheduleLoad(
    account: GoogleAccount,
    indicator: ProgressIndicator
  ): CompletableFuture<DetailsLoadingResult<GoogleUserDetailed>> {

    val credentials = try {
      getUserCredentials(account) ?: return CompletableFuture.completedFuture(noCredentials())
    }
    catch (e: TimeoutException) {
      return CompletableFuture.completedFuture(failedToLoadInfo())
    }

    return userInfoService.acquireUserInfo(credentials.accessToken, indicator).thenCompose { userInfo ->
      loadDetails(account, userInfo, indicator)
    }.exceptionally {
      when(it.cause) {
        is GoogleAuthorizedUserException -> unauthenticatedUser()
        is UnrecognizedPropertyException -> {
          LOG.debug(it.localizedMessage)
          failedToLoadInfo()
        }
        else -> failedToLoadInfo()
      }
    }
  }

  @RequiresEdt
  private fun loadDetails(account: GoogleAccount, userInfo: GoogleUserInfo, progressIndicator: ProgressIndicator) =
    ProgressManager.getInstance().submitIOTask(progressIndicator) {
      val url = URL(userInfo.picture)
      val image = ImageIO.read(url)
      val details = GoogleUserDetailed(userInfo.name, userInfo.id, userInfo.givenName, userInfo.familyName, userInfo.locale)

      DetailsLoadingResult(details, image, null, false)
    }.successOnEdt(ModalityState.any()) {
      accountsListModel.accountsListModel.contentsChanged(account)
      it
    }

  @RequiresEdt
  private fun getUserCredentials(account: GoogleAccount): GoogleCredentials? =
    accountsListModel.newCredentials.getOrElse(account) {
      getOrUpdateUserCredentials(oAuthService, accountManager, account)
    }

  private fun noCredentials() =
    DetailsLoadingResult<GoogleUserDetailed>(null, null, MarkdownBundle.message("markdown.google.accounts.token.missing"), true)

  private fun failedToLoadInfo() =
    DetailsLoadingResult<GoogleUserDetailed>(null, null, MarkdownBundle.message("markdown.google.accounts.failed.load.user"), true)

  private fun unauthenticatedUser() =
    DetailsLoadingResult<GoogleUserDetailed>(null, null, MarkdownBundle.message("markdown.google.accounts.user.unauthenticated.error"), true)
}
