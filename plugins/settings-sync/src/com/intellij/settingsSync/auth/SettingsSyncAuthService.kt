package com.intellij.settingsSync.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBAccountInfoService
import java.util.*

internal interface SettingsSyncAuthService {
  companion object {
    fun getInstance(): SettingsSyncAuthService = ApplicationManager.getApplication().getService(SettingsSyncAuthService::class.java)
  }

  fun isLoggedIn(): Boolean
  fun getUserData(): JBAccountInfoService.JBAData?
  fun login()
  fun isLoginAvailable(): Boolean
  fun addListener(listener: Listener, disposable: Disposable)

  interface Listener : EventListener {
    fun stateChanged()
  }
}