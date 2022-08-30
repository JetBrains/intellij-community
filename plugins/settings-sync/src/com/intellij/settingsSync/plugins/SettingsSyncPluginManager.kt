package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.*
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

  private val LOCK = Object()

  private val pluginStateListener = object : PluginStateListener {

    override fun install(descriptor: IdeaPluginDescriptor) {
      LOG.info("Installed plugin ${descriptor.pluginId.idString}")
      synchronized(LOCK) {
        sessionUninstalledPlugins.remove(descriptor.pluginId.idString)
        if (shouldSaveState(descriptor)) {
          savePluginState(descriptor)
        }
      }
    }

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
      val idString = descriptor.pluginId.idString
      LOG.info("Uninstalled plugin ${idString}")
      synchronized(LOCK) {
        state.plugins[idString]?.let {
          it.isEnabled = false
          LOG.info("${idString} state changed to disabled.")
        }
        sessionUninstalledPlugins.add(idString)
      }
    }
  }

  private val disabledListener = Runnable {
    val disabledIds = DisabledPluginsState.getDisabledIds()
    val disabledIdStrings = HashSet<String>()
    synchronized(LOCK) {
      disabledIds.forEach {
        LOG.info("Plugin ${it.idString} is disabled.")
        disabledIdStrings.add(it.idString)
        if (!state.plugins.containsKey(it.idString)) {
          PluginManagerCore.getPlugin(it)?.let { descriptor ->
            if (isPluginSyncEnabled(
                descriptor.pluginId.idString, descriptor.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(descriptor))) {
              savePluginState(descriptor)
            }
          }
        }
      }
      val droppedKeys = ArrayList<String>()
      state.plugins.forEach { entry ->
        val pluginId = PluginId.getId(entry.key)
        PluginManagerCore.findPlugin(pluginId)?.let {
          if (disabledIdStrings.contains(entry.key)) {
            entry.value.isEnabled = false
            LOG.info("${pluginId.idString} state changed to disabled")
          }
          else {
            if (it.isBundled) {
              droppedKeys.add(entry.key)
            }
            else {
              entry.value.isEnabled = true
              LOG.info("${pluginId.idString} state changed to enabled")
            }
          }
        }
      }
      droppedKeys.forEach {
        state.plugins.remove(it)
        LOG.info("Removed plugin data for ${it}")
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
    synchronized(LOCK) {
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
    LOG.info("Updated plugin state: ${idString}, enabled = ${pluginData.isEnabled}")
    return pluginData
  }

  fun pushChangesToIde() {
    val pluginManagerProxy = PluginManagerProxy.getInstance()
    val installer = pluginManagerProxy.createInstaller()
    synchronized(LOCK) {
      this.state.plugins.forEach { (pluginId, pluginData) ->
        val plugin = findPlugin(pluginId)
        if (plugin != null) {
          if (isPluginSyncEnabled(plugin.pluginId.idString, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin))) {
            if (pluginData.isEnabled != isPluginEnabled(plugin.pluginId)) {
              if (pluginData.isEnabled) {
                pluginManagerProxy.enablePlugin(plugin.pluginId)
                LOG.info("Enabled plugin: ${plugin.pluginId.idString}")
              }
              else {
                pluginManagerProxy.disablePlugin(plugin.pluginId)
                LOG.info("Disabled plugin: ${plugin.pluginId.idString}")
              }
            }
          }
        }
        else {
          if (pluginData.isEnabled &&
              isPluginSyncEnabled(pluginId, false, pluginData.category) &&
              checkDependencies(pluginId, pluginData)) {
            val newPluginId = PluginId.getId(pluginId)
            installer.addPluginId(newPluginId)
            LOG.info("New plugin installation requested: ${newPluginId.idString}")
          }
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


  @TestOnly
  fun getPluginStateListener() = pluginStateListener

  override fun dispose() {
    DisabledPluginsState.removeDisablePluginListener(disabledListener)
    PluginStateManager.removeStateListener(pluginStateListener)
  }
}