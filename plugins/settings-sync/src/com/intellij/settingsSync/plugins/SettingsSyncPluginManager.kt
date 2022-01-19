package com.intellij.settingsSync.plugins

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager.Companion.FILE_SPEC

@State(name = "SettingsSyncPluginData", storages = [Storage(FILE_SPEC)])
class SettingsSyncPluginManager : PersistentStateComponent<SettingsSyncPluginManager.PluginData> {

  companion object {
    fun getInstance() = ApplicationManager.getApplication().getService(SettingsSyncPluginManager::class.java)

    const val FILE_SPEC = "settingsSyncPluginData.xml"
  }

  private var state = PluginData()

  private var noUpdateFromIde : Boolean = false

  override fun getState(): PluginData {
    updateStateFromIde()
    return state
  }

  override fun loadState(state: PluginData) {
    this.state = state
  }

  class PluginData : BaseState() {
    var plugins by map<String, SettingsSyncPluginState>()
  }

  class SettingsSyncPluginState : BaseState() {
    var isEnabled by property(true)
    var requires by stringSet()
  }

  private fun updateStateFromIde() {
    if (noUpdateFromIde) return
    PluginManagerCore.getPlugins().forEach {
      val idString = it.pluginId.idString
      if (shouldSaveState(it)) {
        var pluginState = state.plugins.get(idString)
        if (pluginState == null) {
          pluginState = SettingsSyncPluginState()
          it.dependencies.forEach { dependency ->
            if (!dependency.isOptional) {
              pluginState.requires.add(dependency.pluginId.idString)
              pluginState.intIncrementModificationCount()
            }
          }
          state.plugins[idString] = pluginState
        }
        pluginState.isEnabled = it.isEnabled
      }
      else {
        if (state.plugins.containsKey(idString)) {
          state.plugins.remove(idString)
        }
      }
    }
  }

  fun pushChangesToIDE() {
    val installer = SettingsSyncPluginInstaller()
    this.state.plugins.forEach { mapEntry ->
      val plugin = findPlugin(mapEntry.key)
      if (plugin != null) {
        if (isPluginSyncEnabled(plugin)) {
          if (mapEntry.value.isEnabled != plugin.isEnabled) {
            if (mapEntry.value.isEnabled) {
              PluginManagerCore.enablePlugin(plugin.pluginId)
            }
            else {
              PluginManagerCore.disablePlugin(plugin.pluginId)
            }
          }
        }
      }
      else {
        if (checkDependencies(mapEntry.value)) {
          val newPluginId = PluginId.getId(mapEntry.key)
          installer.addPluginId(newPluginId)
        }
      }
    }
    installer.installUnderProgress()
  }

  private fun findPlugin(idString : String) : IdeaPluginDescriptor? {
    return PluginId.findId(idString)?.let { PluginManagerCore.findPlugin(it) }
  }

  private fun checkDependencies(pluginState: SettingsSyncPluginState) : Boolean {
    pluginState.requires.forEach {
      if (findPlugin(it) == null) {
        return false
      }
    }
    return true
  }

  fun doWithNoUpdateFromIde(runnable: Runnable) {
    noUpdateFromIde = true
    try {
      runnable.run()
    }
    finally {
      noUpdateFromIde = false
    }
  }

  private fun shouldSaveState(plugin: IdeaPluginDescriptor): Boolean {
    return isPluginSyncEnabled(plugin) &&
           (!plugin.isBundled || !plugin.isEnabled || state.plugins.containsKey(plugin.pluginId.idString))
  }

  private fun isPluginSyncEnabled(plugin: IdeaPluginDescriptor): Boolean {
    val settings = SettingsSyncSettings.getInstance()
    return settings.isCategoryEnabled(SettingsCategory.PLUGINS) &&
           (plugin.isBundled && settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID) ||
            settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, plugin.pluginId.idString))
  }

  class StartupInitializer : ApplicationInitializedListener {
    override fun componentsInitialized() {
      getInstance()
    }
  }
}