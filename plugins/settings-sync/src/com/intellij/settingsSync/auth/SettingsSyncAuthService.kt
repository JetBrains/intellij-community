package com.intellij.settingsSync.auth

import com.intellij.settingsSync.communicator.SettingsSyncUserData

interface SettingsSyncAuthService {
  /**
   * short, self-explanatory and unique code name of the provider. May or may not match the
   * @see com.intellij.settingsSync.communicator.SettingsSyncCommunicatorProvider#getProviderCode()
   */
  val providerCode: String

  /**
   * Starts the login procedure
   */
  fun login()

  /**
   * Whether the user has logged in
   */
  fun isLoggedIn(): Boolean

  /**
   * This data is used for in the local git repo as well as UI (if necessary)
   */
  fun getUserData(): SettingsSyncUserData

  /**
   * Indicates if it's currently possible to log in (i.e. all necessary plugins are present and enabled, etc), given the current state of IDE.
   */
  fun isLoginAvailable(): Boolean
}