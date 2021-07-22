package com.intellij.settingsSync.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher
import java.util.*

class SettingsSyncAuthService {
  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(SettingsSyncAuthService::class.java)
  }

  private val evenDispatcher = EventDispatcher.create(Listener::class.java)

  private var loggedIn : Boolean = false // TODO<rv>: Replace fake state

  fun isLoggedIn() : Boolean = loggedIn //TODO<rv>: Implement

  fun login() {
    if (!isLoggedIn()) {
      // TODO<rv>: Implement
      loggedIn = true
      evenDispatcher.multicaster.stateChanged()
    }
  }

  fun logout() {
    if (isLoggedIn()) {
      // TODO<rv>: Implement
      loggedIn = false
      evenDispatcher.multicaster.stateChanged()
    }
  }

  fun getToken() : String? = null  //TODO<rv>: Implement

  fun addListener(listener: Listener, disposable: Disposable) {
    evenDispatcher.addListener(listener, disposable)
  }

  interface Listener : EventListener {
    fun stateChanged()
  }
}