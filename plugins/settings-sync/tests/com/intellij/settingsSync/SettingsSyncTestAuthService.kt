package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.ui.JBAccountInfoService

internal class SettingsSyncTestAuthService : SettingsSyncAuthService {
  override fun isLoggedIn(): Boolean {
    return true
  }

  override fun getUserData(): JBAccountInfoService.JBAData? {
    val id = System.getenv("settings.sync.test.id")
    val loginName = System.getenv("settings.sync.test.login")
    val email = System.getenv("settings.sync.test.id")
    return if (id != null && loginName != null && email != null)
      JBAccountInfoService.JBAData(id, loginName, email)
      else null
  }

  override fun login() {
  }

  override fun isLoginAvailable(): Boolean {
    return false
  }

  override fun addListener(listener: SettingsSyncAuthService.Listener, disposable: Disposable) {
  }
}