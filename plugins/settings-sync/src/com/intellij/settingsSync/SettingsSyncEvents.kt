package com.intellij.settingsSync

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

// used by rider
@ApiStatus.Internal
@Service
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

  fun fireLoginStateChanged() {
    eventDispatcher.multicaster.loginStateChanged()
  }

  companion object {
    @RequiresBlockingContext
    fun getInstance(): SettingsSyncEvents = service<SettingsSyncEvents>()
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
  fun loginStateChanged() {}
}

sealed class RestartReason: Comparable<RestartReason> {
  abstract val sortingPriority: Int

  @NlsSafe
  abstract fun getSingleReasonNotificationMessage(): String

  @NlsSafe
  abstract fun getMultiReasonNotificationListEntry(number: Int): String

  override fun compareTo(other: RestartReason): Int {
    return this.sortingPriority.compareTo(other.sortingPriority)
  }
}

internal class RestartForPluginInstall(val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 0

  override fun getSingleReasonNotificationMessage(): String {
    return SettingsSyncBundle.message("sync.notification.restart.message.plugin.install", plugins.size, plugins.take(2).joinToString(", "))
  }

  override fun getMultiReasonNotificationListEntry(number: Int): String {
    return "$number. " + SettingsSyncBundle.message("sync.notification.restart.message.list.entry.plugin.install", plugins.size, plugins.take(2).joinToString(", "))
  }
}

internal class RestartForPluginEnable(val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 1

  override fun getSingleReasonNotificationMessage(): String {
    return SettingsSyncBundle.message("sync.notification.restart.message.plugin.enable", plugins.size, plugins.take(2).joinToString(", "))
  }

  override fun getMultiReasonNotificationListEntry(number: Int): String {
    return "$number. " + SettingsSyncBundle.message("sync.notification.restart.message.list.entry.plugin.enable", plugins.size, plugins.take(2).joinToString(", "))
  }
}

internal class RestartForPluginDisable(val plugins: Collection<String>) : RestartReason() {
  override val sortingPriority = 2

  override fun getSingleReasonNotificationMessage(): String {
    return SettingsSyncBundle.message("sync.notification.restart.message.plugin.disable", plugins.size, plugins.take(2).joinToString(", "))
  }

  override fun getMultiReasonNotificationListEntry(number: Int): String {
    return "$number. " + SettingsSyncBundle.message("sync.notification.restart.message.list.entry.plugin.disable", plugins.size, plugins.take(2).joinToString(", "))
  }
}