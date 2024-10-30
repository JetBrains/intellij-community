package com.intellij.settingsSync.auth

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger.getInstance
import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.settingsSync.SettingsSyncPromotion
import com.intellij.ui.JBAccountInfoService
import java.util.function.Consumer

internal class SettingsSyncDefaultAuthService : SettingsSyncAuthService {

  companion object {
    private val LOG = logger<SettingsSyncDefaultAuthService>()
  }

  @Volatile
  private var invalidatedIdToken: String? = null

  override fun isLoggedIn(): Boolean {
    return isTokenValid(getAccountInfoService()?.idToken)
  }

  private fun isTokenValid(token: String?): Boolean {
    return token != null && token != invalidatedIdToken
  }

  override fun getUserData(): JBAccountInfoService.JBAData? {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      return DummyJBAccountInfoService.userData
    }
    return getAccountInfoService()?.userData
  }

  override val idToken: String?
    get() {
      val token = getAccountInfoService()?.idToken
      if (!isTokenValid(token)) return null
      return token
    }

  override fun login() {
    if (!isLoggedIn()) {
      val accountInfoService = getAccountInfoService()
      val loginMetadata = hashMapOf(
        "from.settings.sync" to "true"
      )
      if (SettingsSyncPromotion.promotionShownThisSession) {
        loginMetadata["from.settings.sync.promotion"] = "true"
      }
      if (accountInfoService != null) {
        try {
          val loginSession: JBAccountInfoService.LoginSession? = accountInfoService.startLoginSession(
            JBAccountInfoService.LoginMode.AUTO, loginMetadata)

          loginSession!!.onCompleted().thenAccept(Consumer<JBAccountInfoService.LoginResult> {
              SettingsSyncEvents.getInstance().fireLoginStateChanged()
            })
        }
        catch (e: Throwable) {
          LOG.error(e)
          SettingsSyncEvents.getInstance().fireLoginStateChanged()
        }
      }
    }
  }

  override fun isLoginAvailable(): Boolean = getAccountInfoService() != null

  override fun invalidateJBA(idToken: String) {
    if (invalidatedIdToken == idToken) return

    LOG.warn("Invalidating JBA Token")
    invalidatedIdToken = idToken
    SettingsSyncEvents.getInstance().fireLoginStateChanged()
  }

  // Extracted to simplify testing
  override fun getAccountInfoService(): JBAccountInfoService? {
    if (ApplicationManagerEx.isInIntegrationTest() || System.getProperty("settings.sync.test.auth") == "true") {
      return DummyJBAccountInfoService
    }
    return JBAccountInfoService.getInstance()
  }
}