package com.intellij.settingsSync.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.EventDispatcher

internal class SettingsSyncDefaultAuthService : SettingsSyncAuthService {

  private val evenDispatcher = EventDispatcher.create(SettingsSyncAuthService.Listener::class.java)

  override fun isLoggedIn() : Boolean {
    return JBAccountInfoService.getInstance()?.userData != null
  }

  override fun getUserData(): JBAccountInfoService.JBAData? {
    if(ApplicationManagerEx.isInIntegrationTest()){
      return JBAccountInfoService.JBAData("integrationTest", "testLogin", "testEmail@example.com")
    }
    return JBAccountInfoService.getInstance()?.userData
  }

  override fun login() {
    if (!isLoggedIn()) {
      JBAccountInfoService.getInstance()?.invokeJBALogin(
        {
          evenDispatcher.multicaster.stateChanged()
        }, {
          evenDispatcher.multicaster.stateChanged()
        })
    }
  }

  override fun isLoginAvailable(): Boolean = JBAccountInfoService.getInstance() != null

  override fun addListener(listener: SettingsSyncAuthService.Listener, disposable: Disposable) {
    evenDispatcher.addListener(listener, disposable)
  }
}