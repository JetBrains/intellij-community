package com.intellij.settingsSync.jba.auth

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.SettingsSyncPromotion
import com.intellij.ui.JBAccountInfoService
import java.util.function.Consumer

internal class JBAAuthService : SettingsSyncAuthService {

  companion object {
    private val LOG = logger<JBAAuthService>()
  }

  @Volatile
  private var invalidatedIdToken: String? = null

  override fun isLoggedIn(): Boolean {
    return isTokenValid(getAccountInfoService()?.idToken)
  }

  private fun isTokenValid(token: String?): Boolean {
    return token != null && token != invalidatedIdToken
  }

  override fun getUserData() = fromJBAData(
      if (ApplicationManagerEx.isInIntegrationTest()) {
        DummyJBAccountInfoService.userData
      } else {
        getAccountInfoService()?.userData
      }
    )

  private fun fromJBAData(jbaData: JBAccountInfoService.JBAData?) : SettingsSyncUserData {
    if (jbaData == null) {
      return SettingsSyncUserData.EMPTY
    } else {
      return SettingsSyncUserData(
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
            JBAccountInfoService.LoginMode.AUTO, null, loginMetadata)

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