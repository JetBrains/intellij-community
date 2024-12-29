// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package intellij.settingsSync.fileSystem

import com.intellij.settingsSync.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.communicator.SettingsSyncCommunicatorProvider

class FSCommunicatorProvider : SettingsSyncCommunicatorProvider {
  private val authServiceLazy = lazy<FSAuthService> { FSAuthService() }

  override val providerCode: String
    get() = "fs"

  override val authService: SettingsSyncAuthService
    get() = authServiceLazy.value

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator? {
    return FSCommunicator(userId)
  }

}