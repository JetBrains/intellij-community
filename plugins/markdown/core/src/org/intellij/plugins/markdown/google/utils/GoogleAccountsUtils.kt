// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.intellij.collaboration.auth.ui.AccountsPanelFactory.accountsPanel
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.alsoIfNull
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.GoogleAppCredentialsException
import org.intellij.plugins.markdown.google.accounts.GoogleAccountManager
import org.intellij.plugins.markdown.google.accounts.GoogleAccountsDetailsProvider
import org.intellij.plugins.markdown.google.accounts.GoogleAccountsListModel
import org.intellij.plugins.markdown.google.accounts.GoogleUserInfoService
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.authorization.GoogleOAuthService
import org.intellij.plugins.markdown.google.authorization.getGoogleAuthRequest
import org.intellij.plugins.markdown.google.authorization.getGoogleRefreshRequest
import org.intellij.plugins.markdown.google.ui.GoogleChooseAccountDialog
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

internal object GoogleAccountsUtils {

  data class GoogleAppCredentials(val clientId: String, val clientSecret: String)

  val jacksonMapper: ObjectMapper get() = jacksonObjectMapper()
  private val LOG = logger<GoogleOAuthService>()

  @RequiresEdt
  fun chooseAccount(project: Project): Credential? {
    val accountsListModel = GoogleAccountsListModel()
    val accountManager = service<GoogleAccountManager>()
    val oAuthService = service<GoogleOAuthService>()

    if (!GoogleChooseAccountDialog(project, accountsListModel, accountManager).showAndGet()) return null
    updateAccountsList(accountsListModel, accountManager)

    return try {
      val selectedAccount = accountsListModel.selectedAccount ?: error("The selected account cannot be null")
      val accountCredentials: GoogleCredentials = getOrUpdateUserCredentials(oAuthService, accountManager, selectedAccount)
                                                  ?: tryToReLogin(project)
                                                  ?: return null

      createCredentialsForGoogleApi(accountCredentials)
    }
    catch (e: TimeoutException) {
      LOG.debug(e)
      null
    }
    catch (e: IllegalStateException) {
      LOG.debug(e)
      null
    }
  }

  fun tryToReLogin(project: Project): GoogleCredentials? {
    var credentialsFuture: CompletableFuture<GoogleCredentials> = CompletableFuture()

    return try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        val request = getGoogleAuthRequest()
        credentialsFuture = service<GoogleOAuthService>().authorize(request)
        ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture)
      }, MarkdownBundle.message("markdown.google.account.login.progress.title"), true, project)
    }
    catch (t: Throwable) {
      credentialsFuture.cancel(true)
      null
    }
  }

  /**
   * Returns the user's credentials if the access token is still valid, otherwise updates the credentials and returns updated.
   */
  @RequiresEdt
  fun getOrUpdateUserCredentials(oAuthService: GoogleOAuthService,
                                 accountManager: GoogleAccountManager,
                                 account: GoogleAccount): GoogleCredentials? =
    accountManager.findCredentials(account)?.let { credentials ->
      if (credentials.isAccessTokenValid()) return credentials

      val refreshRequest = getGoogleRefreshRequest(credentials.refreshToken)
      val credentialFuture = oAuthService.updateAccessToken(refreshRequest)

      return try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
          val newCred = ProgressIndicatorUtils.awaitWithCheckCanceled(credentialFuture)
          accountManager.updateAccount(account, newCred)

          newCred
        }, MarkdownBundle.message("markdown.google.account.update.credentials.progress.title"), true, null)
      }
      catch (e: RuntimeException) {
        val message = e.cause?.cause?.localizedMessage.alsoIfNull { e.localizedMessage }
        LOG.warn("Failed to update user credentials:\n$message")

        null
      }
    }

  /**
   * @return the panel for selecting Google accounts with the ability to edit the list of accounts.
   */
  fun createGoogleAccountPanel(disposable: Disposable,
                               accountsListModel: GoogleAccountsListModel,
                               accountManager: GoogleAccountManager): DialogPanel {
    val oAuthService = service<GoogleOAuthService>()
    val userInfoService = service<GoogleUserInfoService>()

    val indicatorsProvider = ProgressIndicatorsProvider().also {
      Disposer.register(disposable, it)
    }
    val detailsProvider = GoogleAccountsDetailsProvider(
      indicatorsProvider,
      accountManager,
      accountsListModel,
      oAuthService,
      userInfoService
    )

    return panel {
      row {
        accountsPanel(accountManager, accountsListModel, detailsProvider, disposable, false)
          .horizontalAlign(HorizontalAlign.FILL)
          .verticalAlign(VerticalAlign.FILL)
      }.resizableRow()
    }
  }

  /**
   * Credentials for Installed application from google console.
   * Note: In this context, the client secret is obviously not treated as a secret.
   * These credentials are fine to be public: https://developers.google.com/identity/protocols/oauth2#installed
   */
  @RequiresBackgroundThread
  fun getGoogleAppCredentials(): GoogleAppCredentials? {
    val googleAppCredUrl = "https://www.jetbrains.com/config/markdown.json"

    try {
      val client = HttpClient.newHttpClient()
      val httpRequest: HttpRequest = HttpRequest.newBuilder()
        .uri(URI.create(googleAppCredUrl))
        .header("Content-Type", "application/json")
        .GET()
        .build()
      val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())

      if (response.statusCode() == 200) {
        with(jacksonMapper) {
          val credentials = readTree(response.body()).get("google").get("auth")
          val clientId = credentials.get("secret-id").asText()
          val clientSecret = credentials.get("client-secret").asText()

          return GoogleAppCredentials(clientId, clientSecret)
        }
      }
      else {
        LOG.info("Status code: ${response.statusCode()}\n${response.body()}")
        return null
      }
    }
    catch (e: ConnectException) {
      LOG.info(GoogleAppCredentialsException())
      return null
    }
  }

  /**
   * Converts internal GoogleCredentials to Credentials that the Google API can work with
   */
  fun createCredentialsForGoogleApi(credentials: GoogleCredentials): Credential {
    val tokenResponse = getTokenResponse(credentials)

    return GoogleCredential().setFromTokenResponse(tokenResponse)
  }

  @RequiresEdt
  private fun updateAccountsList(accountsListModel: GoogleAccountsListModel, accountManager: GoogleAccountManager) {
    val newTokensMap = mutableMapOf<GoogleAccount, GoogleCredentials?>()
    newTokensMap.putAll(accountsListModel.newCredentials)
    for (account in accountsListModel.accounts) {
      newTokensMap.putIfAbsent(account, null)
    }
    accountManager.updateAccounts(newTokensMap)
    accountsListModel.clearNewCredentials()
  }

  /**
   * Converts GoogleCredentials to TokenResponse to further get the Credential needed to work with the Drive API
   */
  private fun getTokenResponse(credentials: GoogleCredentials): TokenResponse = TokenResponse().apply {
    accessToken = credentials.accessToken
    tokenType = credentials.tokenType
    scope = credentials.scope
    expiresInSeconds = credentials.expiresIn
  }
}
