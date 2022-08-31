// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.ui.LazyLoadingAccountsDetailsProvider
import com.intellij.collaboration.auth.ui.cancelOnRemoval
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.GoogleAuthorizedUserException
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.accounts.data.GoogleUserDetailed
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.getOrUpdateUserCredentials
import java.awt.Image
import java.net.URL
import javax.imageio.ImageIO

internal class GoogleAccountsDetailsProvider(
  scope: CoroutineScope,
  private val accountManager: GoogleAccountManager,
  private val accountsListModel: GoogleAccountsListModel,
  private val oAuthService: GoogleOAuthService,
  private val userInfoService: GoogleUserInfoService
) : LazyLoadingAccountsDetailsProvider<GoogleAccount, GoogleUserDetailed>(scope, EmptyIcon.ICON_16) {

  init {
    cancelOnRemoval(accountsListModel.accountsListModel)
  }

  override suspend fun loadDetails(account: GoogleAccount): Result<GoogleUserDetailed> {
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

  override suspend fun loadAvatar(account: GoogleAccount, url: String): Image? = withContext(Dispatchers.IO) {
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
