package com.intellij.settingsSync.auth

import com.intellij.ui.JBAccountInfoService
import java.util.function.Consumer

object DummyJBAccountInfoService : JBAccountInfoService {

  private val dummyUserData = JBAccountInfoService.JBAData("integrationTest", "testLogin", "testEmail@example.com")

  override fun getUserData(): JBAccountInfoService.JBAData = dummyUserData

  override fun getIdToken(): String {
    return "DUMMYTOKEN"
  }

  override fun invokeJBALogin(userIdConsumer: Consumer<in String>?, onFailure: Runnable?) {
    //TODO("Not yet implemented")
  }
}