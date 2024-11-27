package com.intellij.settingsSync.communicator

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.settingsSync.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.auth.SettingsSyncAuthService

interface SettingsSyncCommunicatorProvider {

  /**
   * a unique code which identifies the provider (for example, "jba")
   */
  val providerCode: String

  /**
   * Authentication service. Is used during the login process as well as for storing the file
   */
  val authService: SettingsSyncAuthService

  /**
   * Creates a communicator (using the login data from authService)
   */
  fun createCommunicator(): SettingsSyncRemoteCommunicator?

  companion object {
    @JvmField
    val PROVIDER_EP = ExtensionPointName.create<SettingsSyncCommunicatorProvider>("com.intellij.settingsSync.communicatorProvider")
  }
}

data class SettingsSyncUserData(
  val name: String?,
  val email: String?,
) {
  companion object {
    val EMPTY = SettingsSyncUserData(null, null)
  }
}