package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager.Companion.FILE_SPEC
import org.jetbrains.annotations.TestOnly

@State(name = "SettingsSyncPlugins", storages = [Storage(FILE_SPEC)])
internal class SettingsSyncPluginManager : PersistentStateComponent<SettingsSyncPluginManager.SyncPluginsState>, Disposable {

  internal companion object {
    fun getInstance(): SettingsSyncPluginManager = ApplicationManager.getApplication().getService(SettingsSyncPluginManager::class.java)

    const val FILE_SPEC = "settingsSyncPlugins.xml"

    val LOG = logger<SettingsSyncPluginManager>()
  }

  private val pluginStateListener = object : PluginStateListener {

    override fun install(descriptor: IdeaPluginDescriptor) {
      sessionUninstalledPlugins.remove(descriptor.pluginId.idString)
      if (shouldSaveState(descriptor)) {
        savePluginState(descriptor)
      }
    }

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
      val idString = descriptor.pluginId.idString
      state.plugins[idString]?.let { it.isEnabled = false }
      sessionUninstalledPlugins.add(idString)
    }
  }

  private val disabledListener = Runnable {
    val disabledPlugins = DisabledPluginsState.getDisabledIds()
    val disabledIds = HashSet<String>()
    disabledPlugins.forEach {
      disabledIds.add(it.idString)
      if (!state.plugins.containsKey(it.idString)) {
        PluginManagerCore.getPlugin(it)?.let { descriptor ->
          if (shouldSaveState(descriptor)) {
            savePluginState(descriptor)
          }
        }
      }
    }
    state.plugins.forEach { entry ->
      val pluginId = PluginId.getId(entry.key)
      PluginManagerCore.findPlugin(pluginId)?.let {
        entry.value.isEnabled = !disabledIds.contains(entry.key)
      }
    }
  }

  init {
    PluginStateManager.addStateListener(pluginStateListener)
    DisabledPluginsState.addDisablePluginListener(disabledListener)
  }

  private var state = SyncPluginsState()

  private val sessionUninstalledPlugins = HashSet<String>()


  init {
    updateStateFromIde()
  }

  override fun getState(): SyncPluginsState {
    return state
  }

  override fun loadState(state: SyncPluginsState) {
    this.state = state
  }

  @TestOnly
  fun clearState() {
    state.plugins.clear()
  }

  class SyncPluginsState : BaseState() {
    var plugins by map<String, PluginData>()
  }

  class PluginData : BaseState() {
    var isEnabled by property(true)
    var dependencies by stringSet()
    var category by enum(SettingsCategory.PLUGINS)
  }

  private fun updateStateFromIde() {
    PluginManagerProxy.getInstance().getPlugins().forEach {
      val idString = it.pluginId.idString
      if (shouldSaveState(it)) {
        val pluginData = state.plugins[idString] ?: savePluginState(it)
        pluginData.isEnabled = it.isEnabled && !sessionUninstalledPlugins.contains(idString)
      }
      else {
        if (state.plugins.containsKey(idString)) {
          state.plugins.remove(idString)
        }
      }
    }
  }

  private fun savePluginState(descriptor: IdeaPluginDescriptor): PluginData {
    val pluginData = PluginData()
    val idString = descriptor.pluginId.idString
    pluginData.category = SettingsSyncPluginCategoryFinder.getPluginCategory(descriptor)
    descriptor.dependencies.forEach { dependency ->
      if (!dependency.isOptional) {
        pluginData.dependencies.add(dependency.pluginId.idString)
        pluginData.intIncrementModificationCount()
      }
    }
    state.plugins[idString] = pluginData
    return pluginData
  }

  fun pushChangesToIde() {
    val pluginManagerProxy = PluginManagerProxy.getInstance()
    val installer = pluginManagerProxy.createInstaller()
    this.state.plugins.forEach { mapEntry ->
      val plugin = findPlugin(mapEntry.key)
      if (plugin != null) {
        if (isPluginSyncEnabled(plugin.pluginId.idString, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin))) {
          if (mapEntry.value.isEnabled != isPluginEnabled(plugin.pluginId)) {
            if (mapEntry.value.isEnabled) {
              pluginManagerProxy.enablePlugin(plugin.pluginId)
              LOG.info("Disabled plugin: ${plugin.pluginId.idString}")
            }
            else {
              pluginManagerProxy.disablePlugin(plugin.pluginId)
              LOG.info("Enabled plugin: ${plugin.pluginId.idString}")
            }
          }
        }
      }
      else {
        if (mapEntry.value.isEnabled &&
            isPluginSyncEnabled(mapEntry.key, false, mapEntry.value.category) &&
            checkDependencies(mapEntry.key, mapEntry.value)) {
          val newPluginId = PluginId.getId(mapEntry.key)
          installer.addPluginId(newPluginId)
          LOG.info("New plugin installation requested: ${newPluginId.idString}")
        }
      }
    }
    installer.installPlugins()
  }

  private fun isPluginEnabled(pluginId: PluginId) = !DisabledPluginsState.getDisabledIds().contains(pluginId)

  private fun findPlugin(idString: String): IdeaPluginDescriptor? {
    return PluginId.findId(idString)?.let { PluginManagerProxy.getInstance().findPlugin(it) }
  }

  private fun checkDependencies(idString: String, pluginState: PluginData): Boolean {
    pluginState.dependencies.forEach {
      if (findPlugin(it) == null) {
        LOG.info("Skipping ${idString} plugin installation due to missing dependency: ${it}")
        return false
      }
    }
    return true
  }

  private fun shouldSaveState(plugin: IdeaPluginDescriptor): Boolean {
    return isPluginSyncEnabled(plugin.pluginId.idString, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin)) &&
           (!plugin.isBundled || !plugin.isEnabled || state.plugins.containsKey(plugin.pluginId.idString))
  }

  private fun isPluginSyncEnabled(idString: String, isBundled: Boolean, category: SettingsCategory): Boolean {
    val settings = SettingsSyncSettings.getInstance()
    return settings.isCategoryEnabled(category) &&
           (category != SettingsCategory.PLUGINS ||
            isBundled && settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID) ||
            settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, idString))
  }


  override fun dispose() {
    DisabledPluginsState.removeDisablePluginListener(disabledListener)
    PluginStateManager.removeStateListener(pluginStateListener)
  }
}