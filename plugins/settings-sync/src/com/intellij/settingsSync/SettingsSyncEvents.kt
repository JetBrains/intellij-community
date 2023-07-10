package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class SettingsSyncEvents : Disposable {

  private val eventDispatcher = EventDispatcher.create(SettingsSyncEventListener::class.java)

  fun addListener(listener: SettingsSyncEventListener) {
    eventDispatcher.addListener(listener)
  }

  fun addListener(listener: SettingsSyncEventListener, parentDisposable: Disposable? = null) {
    eventDispatcher.addListener(listener, parentDisposable ?: this)
  }

  fun removeListener(listener: SettingsSyncEventListener) {
    eventDispatcher.removeListener(listener)
  }

  fun fireSettingsChanged(event: SyncSettingsEvent) {
    eventDispatcher.multicaster.settingChanged(event)
  }

  fun fireCategoriesChanged() {
    eventDispatcher.multicaster.categoriesStateChanged()
  }

  fun fireEnabledStateChanged(syncEnabled: Boolean) {
    eventDispatcher.multicaster.enabledStateChanged(syncEnabled)
  }

  fun fireRestartRequired(reason: RestartReason) {
    eventDispatcher.multicaster.restartRequired(reason)
  }

  companion object {
    fun getInstance(): SettingsSyncEvents = ApplicationManager.getApplication().getService(SettingsSyncEvents::class.java)
  }

  override fun dispose() {
    eventDispatcher.listeners.clear()
  }
}

interface SettingsSyncEventListener : EventListener {
  fun categoriesStateChanged() {}
  fun settingChanged(event: SyncSettingsEvent) {}
  fun enabledStateChanged(syncEnabled: Boolean) {}
  fun restartRequired(reason: RestartReason) {}
}

sealed class RestartReason: Comparable<RestartReason> {
  abstract val sortingPriority: Int
  abstract fun getNotificationSubMessage(): String

  @NlsSafe
  fun getSingleReasonNotificationMessage(): String {
    return SettingsSyncBundle.message("sync.restart.notification.message", getNotificationSubMessage())
  }

  @NlsSafe
  fun getMultiReasonNotificationListEntry(number: Int): String {
    return "$number. ${getNotificationSubMessage().capitalize()}\n"
  }

  private fun String.capitalize() : String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }

  override fun compareTo(other: RestartReason): Int {
    return this.sortingPriority.compareTo(other.sortingPriority)
  }
}

class RestartForPluginInstall(private val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 0
  override fun getNotificationSubMessage(): String {
    return SettingsSyncBundle.message("sync.restart.notification.submessage.plugins", "install", plugins.joinToString(", "))
  }
}

class RestartForPluginEnable(private val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 1
  override fun getNotificationSubMessage(): String {
    return SettingsSyncBundle.message("sync.restart.notification.submessage.plugins", "enable", plugins.joinToString(", "))
  }
}

class RestartForPluginDisable(private val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 2
  override fun getNotificationSubMessage(): String {
    return SettingsSyncBundle.message("sync.restart.notification.submessage.plugins", "disable", plugins.joinToString(", "))
  }
}

object RestartForNewUI : RestartReason() {
  override val sortingPriority = 3
  override fun getNotificationSubMessage(): String {
    return SettingsSyncBundle.message("sync.restart.notification.submessage.registry")
  }
}
