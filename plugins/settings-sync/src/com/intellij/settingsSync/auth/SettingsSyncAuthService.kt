package com.intellij.settingsSync.auth

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBAccountInfoService

interface SettingsSyncAuthService {
  companion object {
    fun getInstance(): SettingsSyncAuthService = ApplicationManager.getApplication().getService(SettingsSyncAuthService::class.java)
  }

  val idToken: String?

  fun login()
  fun isLoggedIn(): Boolean
  fun getUserData(): JBAccountInfoService.JBAData?
  fun getAccountInfoService(): JBAccountInfoService?
  fun isLoginAvailable(): Boolean
  fun invalidateJBA(idToken: String)
}