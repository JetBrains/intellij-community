package com.intellij.settingsSync.jba

import com.intellij.openapi.Disposable
import com.intellij.settingsSync.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.jba.auth.JBAAuthService

class JbaCommunicatorProvider : SettingsSyncCommunicatorProvider, Disposable {

  private val authServiceLazy = lazy<JBAAuthService> { JBAAuthService() }

  override val providerCode: String
    get() = "jba"
  override val authService: SettingsSyncAuthService
    get() {
      return authServiceLazy.value
    }

  override fun createCommunicator(): SettingsSyncRemoteCommunicator? = lazy<CloudConfigServerCommunicator> {
    CloudConfigServerCommunicator(null, authServiceLazy.value)
  }.value

  override fun dispose() {
    TODO("Not yet implemented")
  }
}