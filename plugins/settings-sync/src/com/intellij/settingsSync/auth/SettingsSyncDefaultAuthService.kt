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
  private var invalidatedUserId: String? = null

  override fun isLoggedIn() : Boolean {
    val userData = getAccountInfoService()?.userData
    return userData != null && invalidatedUserId != userData.id
  }

  override fun getUserData(): JBAccountInfoService.JBAData? {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      return JBAccountInfoService.JBAData("integrationTest", "testLogin", "testEmail@example.com")
    }
    return getAccountInfoService()?.userData
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

  override fun invalidateJBA(userId: String) {
    if (invalidatedUserId == userId) return

    LOG.warn("Invalidating JBA")
    invalidatedUserId = userId
    SettingsSyncEvents.getInstance().fireLoginStateChanged()
  }

  // Extracted to simplify testing
  override fun getAccountInfoService(): JBAccountInfoService? {
    return JBAccountInfoService.getInstance()
  }
}