package com.intellij.settingsSync.auth

import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.ui.JBAccountInfoService
import java.util.function.Consumer

object DummyJBAccountInfoService : JBAccountInfoService {

  private val dummyUserData = JBAccountInfoService.JBAData("integrationTest", "testLogin", "testEmail@example.com")
  private var _idToken: String? = "DUMMYTOKEN"

  override fun getUserData(): JBAccountInfoService.JBAData = dummyUserData

  private fun refreshIdToken() {
    _idToken = "DUMMYTOKEN" + System.currentTimeMillis()%100
  }

  override fun getIdToken(): String? {
    return _idToken
  }

  override fun invokeJBALogin(userIdConsumer: Consumer<in String>?, onFailure: Runnable?) {
    refreshIdToken()
    SettingsSyncEvents.getInstance().fireLoginStateChanged()
  }
}