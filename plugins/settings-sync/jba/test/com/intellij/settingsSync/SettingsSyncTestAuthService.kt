package com.intellij.settingsSync

import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.auth.DummyJBAccountInfoService
import com.intellij.ui.JBAccountInfoService
import java.awt.Component
import javax.swing.Icon

internal class SettingsSyncTestAuthService : SettingsSyncAuthService {

  override fun getUserData(userId: String): SettingsSyncUserData {
    val id = System.getenv("SETTINGS_SYNC_TEST_ID")
    val loginName = "testLogin"
    val email = "testEmail@example.com"
    val presentableName = "presentableName"
    return SettingsSyncUserData(loginName, email)
  }

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    TODO("Not yet implemented")
  }

  fun getAccountInfoService(): JBAccountInfoService {
    return DummyJBAccountInfoService
  }

  val idToken: String?
    get() = getAccountInfoService().idToken

  override val providerCode: String
    get() = TODO("Not yet implemented")
  override val providerName: String
    get() = TODO("Not yet implemented")
  override val icon: Icon?
    get() = TODO("Not yet implemented")

  override suspend fun login(parentComponent: Component?) : SettingsSyncUserData? {
    return null
  }
}