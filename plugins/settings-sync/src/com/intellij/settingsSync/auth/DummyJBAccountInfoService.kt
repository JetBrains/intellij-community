package com.intellij.settingsSync.auth

import com.intellij.settingsSync.SettingsSyncEvents
import com.intellij.ui.JBAccountInfoService
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

object DummyJBAccountInfoService : JBAccountInfoService {

  private val dummyUserData = JBAccountInfoService.JBAData("integrationTest", "testLogin", "testEmail@example.com", "testPresentableName")
  private var _idToken: String? = "DUMMYTOKEN"

  override fun getUserData(): JBAccountInfoService.JBAData = dummyUserData

  private fun refreshIdToken() {
    _idToken = "DUMMYTOKEN" + System.currentTimeMillis()%100
  }

  override fun getIdToken(): String? {
    return _idToken
  }

  override fun startLoginSession(loginMode: JBAccountInfoService.LoginMode): JBAccountInfoService.LoginSession {
    TODO("Not yet implemented")
  }

  override fun getAvailableLicenses(productCode: String): CompletableFuture<JBAccountInfoService.LicenseListResult> {
    TODO("Not yet implemented")
  }

  override fun issueTrialLicense(productCode: String,
                                 consentOptions: List<String>): CompletableFuture<JBAccountInfoService.LicenseListResult> {
    TODO("Not yet implemented")
  }

  override fun invokeJBALogin(userIdConsumer: Consumer<in String>?, onFailure: Runnable?) {
    refreshIdToken()
    SettingsSyncEvents.getInstance().fireLoginStateChanged()
  }

  override fun getServiceConfiguration(): CompletableFuture<JBAccountInfoService.JbaServiceConfiguration> {
    TODO("Not yet implemented")
  }
}
