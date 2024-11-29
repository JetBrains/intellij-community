package com.intellij.settingsSync

import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.auth.DummyJBAccountInfoService
import com.intellij.ui.JBAccountInfoService

internal class SettingsSyncTestAuthService : SettingsSyncAuthService {
  override fun isLoggedIn(): Boolean {
    return true
  }

  override fun getUserData(): SettingsSyncUserData {
    val id = System.getenv("SETTINGS_SYNC_TEST_ID")
    val loginName = "testLogin"
    val email = "testEmail@example.com"
    val presentableName = "presentableName"
    return SettingsSyncUserData(loginName, email)
  }

  fun getAccountInfoService(): JBAccountInfoService {
    return DummyJBAccountInfoService
  }

  val idToken: String?
    get() = getAccountInfoService().idToken

  override val providerCode: String
    get() = TODO("Not yet implemented")

  override fun login() {
  }

  override fun isLoginAvailable(): Boolean {
    return false
  }
}