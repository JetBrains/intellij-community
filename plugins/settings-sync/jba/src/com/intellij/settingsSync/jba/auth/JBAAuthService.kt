package com.intellij.settingsSync.jba.auth

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.SettingsSyncPromotion
import com.intellij.ui.JBAccountInfoService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import java.util.concurrent.CancellationException

internal class JBAAuthService : SettingsSyncAuthService {

  companion object {
    private val LOG = logger<JBAAuthService>()
  }

  @Volatile
  private var invalidatedIdToken: String? = null

  private fun isTokenValid(token: String?): Boolean {
    return token != null && token != invalidatedIdToken
  }

  override fun getUserData(userId: String) = fromJBAData(
      if (ApplicationManagerEx.isInIntegrationTest()) {
        DummyJBAccountInfoService.userData
      } else {
        getAccountInfoService()?.userData
      }
    )

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    val userData = getUserData("dummy")
    if (userData != null) {
      return listOf(userData)
    } else {
      return emptyList()
    }
  }

  private fun fromJBAData(jbaData: JBAccountInfoService.JBAData?) : SettingsSyncUserData? {
    if (jbaData == null) {
      return null
    } else {
      return SettingsSyncUserData(
        jbaData.id,
        providerCode,
        jbaData.loginName,
        jbaData.email,
      )
    }
  }

  val idToken: String?
    get() {
      val token = getAccountInfoService()?.idToken
      if (!isTokenValid(token)) return null
      return token
    }
  override val providerCode: String
    get() = "jba"
  override val providerName: String
    get() = "JetBrains"

  override val icon = AllIcons.Ultimate.IdeaUltimatePromo

  override suspend fun login(): Deferred<SettingsSyncUserData?> {
    return coroutineScope {
      val accountInfoService = getAccountInfoService()
      val loginMetadata = hashMapOf(
        "from.settings.sync" to "true"
      )
      if (SettingsSyncPromotion.promotionShownThisSession) {
        loginMetadata["from.settings.sync.promotion"] = "true"
      }
      val retval = CompletableDeferred<SettingsSyncUserData?>(parent = coroutineContext.job)
      if (accountInfoService != null) {
        try {
          val loginSession: JBAccountInfoService.LoginSession = accountInfoService.startLoginSession(
            JBAccountInfoService.LoginMode.AUTO, null, loginMetadata)

          loginSession.onCompleted().exceptionally{ exc ->
            if (exc is CancellationException) {
              LOG.warn("Login cancelled")
            } else {
              LOG.warn("Login failed", exc)
            }
            null
          }.thenApply{loginResult ->
            if (loginResult is JBAccountInfoService.LoginResult.LoginSuccessful) {
              retval.complete(fromJBAData(loginResult.jbaUser))
            } else if (loginResult is JBAccountInfoService.LoginResult.LoginFailed) {
              LOG.warn("Login failed: ${loginResult.errorMessage}")
              retval.complete(null)
            } else {
              LOG.warn("Unknown login result: $loginResult")
              retval.complete(null)
            }
          }
        }
        catch (e: Throwable) {
          LOG.error(e)
          retval.complete(null)
        }
      } else {
        LOG.error("JBA auth service is not available!")
        retval.complete(null)
      }
      retval
    }
  }

  fun invalidateJBA(idToken: String) {
    if (invalidatedIdToken == idToken) return

    LOG.warn("Invalidating JBA Token")
    invalidatedIdToken = idToken
    SettingsSyncEvents.Companion.getInstance().fireLoginStateChanged()
  }

  // Extracted to simplify testing
  fun getAccountInfoService(): JBAccountInfoService? {
    if (ApplicationManagerEx.isInIntegrationTest() || System.getProperty("settings.sync.test.auth") == "true") {
      return DummyJBAccountInfoService
    }
    return JBAccountInfoService.getInstance()
  }
}