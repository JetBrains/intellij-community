package com.intellij.settingsSync.jba.auth

import com.intellij.ide.gdpr.Version
import com.intellij.settingsSync.core.SettingsSyncEvents
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

  override fun startLoginSession(loginMode: JBAccountInfoService.LoginMode, authProviderId: String?, clientMetadata: Map<String, String>): JBAccountInfoService.LoginSession {
    return object : JBAccountInfoService.LoginSession {
      override fun close() {

      }

      override fun onCompleted(): CompletableFuture<JBAccountInfoService.LoginResult> {
        return CompletableFuture.completedFuture(JBAccountInfoService.LoginResult.LoginSuccessful(dummyUserData))
      }
    }
  }

  override fun getAvailableLicenses(productCode: String): CompletableFuture<JBAccountInfoService.LicenseListResult> {
    TODO("Not yet implemented")
  }

  override fun issueTrialLicense(productCode: String,
                                 consentOptions: List<String>): CompletableFuture<JBAccountInfoService.LicenseListResult> {
    TODO("Not yet implemented")
  }

  override fun dryRunIssueTrialLicense(productCode: String): CompletableFuture<JBAccountInfoService.LicenseListResult> {
    TODO("Not yet implemented")
  }

  override fun recordAgreementAcceptance(productCode: String, documentName: String, acceptedVersion: Version): CompletableFuture<JBAccountInfoService.AgreementAcceptanceResult> {
    TODO("Not yet implemented")
  }

  override fun invokeJBALogin(userIdConsumer: Consumer<in String>?, onFailure: Runnable?) {
    refreshIdToken()
    SettingsSyncEvents.Companion.getInstance().fireLoginStateChanged()
  }

  override fun getServiceConfiguration(): CompletableFuture<JBAccountInfoService.JbaServiceConfiguration> {
    TODO("Not yet implemented")
  }
}