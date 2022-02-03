package com.intellij.settingsSync.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.EventDispatcher
import java.util.*

internal class SettingsSyncAuthService {
  companion object {
    fun getInstance(): SettingsSyncAuthService = ApplicationManager.getApplication().getService(SettingsSyncAuthService::class.java)
  }

  private val evenDispatcher = EventDispatcher.create(Listener::class.java)

  fun isLoggedIn() : Boolean {
    return JBAccountInfoService.getInstance()?.userData != null
  }

  fun getUserData(): JBAccountInfoService.JBAData? {
    return JBAccountInfoService.getInstance()?.userData
  }

  fun login() {
    if (!isLoggedIn()) {
      JBAccountInfoService.getInstance()?.invokeJBALogin(
        {
          evenDispatcher.multicaster.stateChanged()
        }, {
          evenDispatcher.multicaster.stateChanged()
        })
    }
  }

  fun addListener(listener: Listener, disposable: Disposable) {
    evenDispatcher.addListener(listener, disposable)
  }

  interface Listener : EventListener {
    fun stateChanged()
  }
}