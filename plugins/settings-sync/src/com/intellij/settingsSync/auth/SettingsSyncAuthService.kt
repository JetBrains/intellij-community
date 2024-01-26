package com.intellij.settingsSync.auth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBAccountInfoService

internal interface SettingsSyncAuthService {
  companion object {
    fun getInstance(): SettingsSyncAuthService = ApplicationManager.getApplication().getService(SettingsSyncAuthService::class.java)
  }

  fun login()
  fun isLoggedIn(): Boolean
  fun getUserData(): JBAccountInfoService.JBAData?
  fun getAccountInfoService(): JBAccountInfoService?
  fun isLoginAvailable(): Boolean
  fun invalidateJBA(userId: String)
}