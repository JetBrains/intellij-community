package com.intellij.settingsSync.jba

import com.intellij.openapi.Disposable
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.jba.auth.JBAAuthService

class JbaCommunicatorProvider : SettingsSyncCommunicatorProvider, Disposable {

  private val authServiceLazy = lazy<JBAAuthService> { JBAAuthService() }

  override val providerCode: String
    get() = "jba"

  override val authService: SettingsSyncAuthService
    get() {
      return authServiceLazy.value
    }

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator = lazy<CloudConfigServerCommunicator> {
    CloudConfigServerCommunicator(null, authServiceLazy.value)
  }.value

  override fun dispose() {
    TODO("Not yet implemented")
  }
}