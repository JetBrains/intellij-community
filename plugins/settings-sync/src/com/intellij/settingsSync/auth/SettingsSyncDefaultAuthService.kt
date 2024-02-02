package com.intellij.settingsSync.auth

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.ui.JBAccountInfoService

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
      getAccountInfoService()?.invokeJBALogin(
        {
          SettingsSyncEvents.getInstance().fireLoginStateChanged()
        },
        {
          SettingsSyncEvents.getInstance().fireLoginStateChanged()
        })
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