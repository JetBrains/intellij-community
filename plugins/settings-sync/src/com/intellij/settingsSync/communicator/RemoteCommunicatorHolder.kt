package com.intellij.settingsSync.communicator

import com.intellij.openapi.diagnostic.logger
import com.intellij.settingsSync.ServerState
import com.intellij.settingsSync.SettingsSnapshot
import com.intellij.settingsSync.SettingsSyncPushResult
import com.intellij.settingsSync.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.UpdateResult
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.util.concurrency.SynchronizedClearableLazy

object RemoteCommunicatorHolder {

  private val communicatorInternalLazy = SynchronizedClearableLazy<SettingsSyncRemoteCommunicator> {
    getCurrentCommunicator().also {
      logger<RemoteCommunicatorHolder>().warn("Initializing remote communicator: $it")
    }
  }

  fun getRemoteCommunicator(): SettingsSyncRemoteCommunicator = communicatorInternalLazy.value
  fun getAuthService() = getProvider().authService
  fun isAvailable() = communicatorInternalLazy.isInitialized()
  fun invalidateCommunicator() = communicatorInternalLazy.drop().also {
    logger<RemoteCommunicatorHolder>().warn("Invalidating remote communicator: $it")
  }


  private fun getProvider() : SettingsSyncCommunicatorProvider {
    val extensionList = SettingsSyncCommunicatorProvider.PROVIDER_EP.extensionList
    return extensionList.firstOrNull() ?: DummyProvider
  }

  private fun getCurrentCommunicator() : SettingsSyncRemoteCommunicator {
    return getProvider().createCommunicator() ?: DummyCommunicator
  }

  internal object DummyProvider: SettingsSyncCommunicatorProvider {
    override val providerCode: String
      get() = "dummy"
    override val authService: SettingsSyncAuthService
      get() = DummyAuthProvider

    override fun createCommunicator(): SettingsSyncRemoteCommunicator? {
      return DummyCommunicator
    }

  }

  internal object DummyAuthProvider: SettingsSyncAuthService {
    override val providerCode: String
      get() = "dummy"

    override fun login() {
      TODO("Not yet implemented")
    }

    override fun isLoggedIn(): Boolean {
      return false
    }

    override fun getUserData(): SettingsSyncUserData {
      return SettingsSyncUserData.EMPTY
    }

    override fun isLoginAvailable(): Boolean {
      return true
    }
  }

  internal object DummyCommunicator: SettingsSyncRemoteCommunicator {
    override fun checkServerState(): ServerState {
      TODO("Not yet implemented")
    }

    override fun receiveUpdates(): UpdateResult {
      TODO("Not yet implemented")
    }

    override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
      TODO("Not yet implemented")
    }

    override fun createFile(filePath: String, content: String) {
      TODO("Not yet implemented")
    }

    override fun deleteFile(filePath: String) {
      TODO("Not yet implemented")
    }

    override fun isFileExists(filePath: String): Boolean {
      TODO("Not yet implemented")
    }
  }
}