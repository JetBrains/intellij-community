package com.intellij.settingsSync

import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.JBAccountInfoService

internal class SettingsSyncTestAuthService : SettingsSyncAuthService {
  override fun isLoggedIn(): Boolean {
    return true
  }

  override fun getUserData(): JBAccountInfoService.JBAData? {
    val id = System.getenv("SETTINGS_SYNC_TEST_ID")
    val loginName = "testLogin"
    val email = "testEmail@example.com"
    return if (id != null)
      JBAccountInfoService.JBAData(id, loginName, email)
      else null
  }

  override fun getAccountInfoService(): JBAccountInfoService? {
    return null
  }

  override fun login() {
  }

  override fun isLoginAvailable(): Boolean {
    return false
  }

  override fun invalidateJBA(userId: String) {
  }
}