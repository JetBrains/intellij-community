// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader.*
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.GoogleAuthorizedUserException
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserDetailed
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.getOrUpdateUserCredentials
import java.awt.Image
import java.net.URL
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO

internal class GoogleAccountsDetailsLoader(
  private val coroutineScope: CoroutineScope,
  private val accountManager: GoogleAccountManager,
  private val accountsListModel: GoogleAccountsListModel,
  private val oAuthService: GoogleOAuthService,
  private val userInfoService: GoogleUserInfoService
) : AccountsDetailsLoader<GoogleAccount, GoogleUserDetailed> {

  override fun loadDetailsAsync(account: GoogleAccount): Deferred<Result<GoogleUserDetailed>> = coroutineScope.async {
    loadDetails(account)
  }

  private suspend fun loadDetails(account: GoogleAccount): Result<GoogleUserDetailed> {
    return try {
      val credentials = getUserCredentials(account) ?: return noCredentials()
      val userInfo = userInfoService.acquireUserInfo(credentials.accessToken)
      val details = GoogleUserDetailed(userInfo.name, userInfo.id, userInfo.givenName, userInfo.familyName, userInfo.locale,
                                       userInfo.picture)
      return Result.Success(details)
    }
    catch (e: Exception) {
      when (e) {
        is GoogleAuthorizedUserException -> unauthenticatedUser()
        else -> failedToLoadInfo()
      }
    }
  }

  override fun loadAvatarAsync(account: GoogleAccount, url: String): Deferred<Image?> = coroutineScope.async(Dispatchers.IO) {
    ImageIO.read(URL(url))
  }

  @RequiresEdt
  private fun getUserCredentials(account: GoogleAccount): GoogleCredentials? =
    accountsListModel.newCredentials.getOrElse(account) {
      getOrUpdateUserCredentials(oAuthService, accountManager, account)
    }

  private fun noCredentials() =
    Result.Error<GoogleUserDetailed>(MarkdownBundle.message("markdown.google.accounts.token.missing"), true)

  private fun failedToLoadInfo() =
    Result.Error<GoogleUserDetailed>(MarkdownBundle.message("markdown.google.accounts.failed.load.user"), true)

  private fun unauthenticatedUser() =
    Result.Error<GoogleUserDetailed>(MarkdownBundle.message("markdown.google.accounts.user.unauthenticated.error"), true)
}
