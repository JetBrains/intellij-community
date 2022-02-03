package com.intellij.settingsSync.plugins

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
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
  }

  private val pluginStateListener = object : PluginStateListener {

    override fun install(descriptor: IdeaPluginDescriptor) {
      sessionUninstalledPlugins.remove(descriptor.pluginId.idString)
    }

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
      val idString = descriptor.pluginId.idString
      state.plugins[idString]?.let { it.isEnabled = false }
      sessionUninstalledPlugins.add(idString)
    }
  }

  init {
    PluginStateManager.addStateListener(pluginStateListener)
  }

  private var state = SyncPluginsState()

  private var noUpdateFromIde: Boolean = false

  private val sessionUninstalledPlugins = HashSet<String>()

  override fun getState(): SyncPluginsState {
    updateStateFromIde()
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
    if (noUpdateFromIde) return
    PluginManagerProxy.getInstance().getPlugins().forEach {
      val idString = it.pluginId.idString
      if (shouldSaveState(it)) {
        var pluginData = state.plugins[idString]
        if (pluginData == null) {
          pluginData = PluginData()
          pluginData.category = SettingsSyncPluginCategoryFinder.getPluginCategory(it)
          it.dependencies.forEach { dependency ->
            if (!dependency.isOptional) {
              pluginData.dependencies.add(dependency.pluginId.idString)
              pluginData.intIncrementModificationCount()
            }
          }
          state.plugins[idString] = pluginData
        }
        pluginData.isEnabled = it.isEnabled && !sessionUninstalledPlugins.contains(idString)
      }
      else {
        if (state.plugins.containsKey(idString)) {
          state.plugins.remove(idString)
        }
      }
    }
  }

  fun pushChangesToIde() {
    val pluginManagerProxy = PluginManagerProxy.getInstance()
    val installer = pluginManagerProxy.createInstaller()
    this.state.plugins.forEach { mapEntry ->
      val plugin = findPlugin(mapEntry.key)
      if (plugin != null) {
        if (isPluginSyncEnabled(plugin.pluginId.idString, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin))) {
          if (mapEntry.value.isEnabled != plugin.isEnabled) {
            if (mapEntry.value.isEnabled) {
              pluginManagerProxy.enablePlugin(plugin.pluginId)
            }
            else {
              pluginManagerProxy.disablePlugin(plugin.pluginId)
            }
          }
        }
      }
      else {
        if (mapEntry.value.isEnabled &&
            isPluginSyncEnabled(mapEntry.key, false, mapEntry.value.category) &&
            checkDependencies(mapEntry.value)) {
          val newPluginId = PluginId.getId(mapEntry.key)
          installer.addPluginId(newPluginId)
        }
      }
    }
    installer.startInstallation()
  }

  private fun findPlugin(idString: String): IdeaPluginDescriptor? {
    return PluginId.findId(idString)?.let { PluginManagerProxy.getInstance().findPlugin(it) }
  }

  private fun checkDependencies(pluginState: PluginData): Boolean {
    pluginState.dependencies.forEach {
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
    PluginStateManager.removeStateListener(pluginStateListener)
  }

  class StartupInitializer : ApplicationInitializedListener {
    override fun componentsInitialized() {
      getInstance()
    }
  }
}