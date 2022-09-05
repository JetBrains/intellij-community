package com.intellij.settingsSync.plugins

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.settingsSync.FileState
import com.intellij.settingsSync.SettingsSyncSettings
import com.intellij.settingsSync.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager.Companion.FILE_SPEC
import org.jetbrains.annotations.TestOnly

@State(name = "SettingsSyncPlugins", storages = [Storage(FILE_SPEC)])
internal class SettingsSyncPluginManager : PersistentStateComponent<SettingsSyncPluginManager.SyncPluginsState>, Disposable {

  private val pluginInstallationStateListener = PluginInstallationStateListener()
  private val pluginEnabledStateListener = PluginEnabledStateListener()
  private val LOCK = Object()
  private var state = SyncPluginsState()
  private val sessionUninstalledPlugins = HashSet<String>()

  init {
    PluginStateManager.addStateListener(pluginInstallationStateListener)
    PluginManagerProxy.getInstance().addDisablePluginListener(pluginEnabledStateListener, this)
    updateStateFromIde()
  }

  override fun getState(): SyncPluginsState {
    return state
  }

  override fun loadState(state: SyncPluginsState) {
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
      for (plugin in PluginManagerProxy.getInstance().getPlugins()) {
        val idString = plugin.pluginId.idString
        if (shouldSaveState(plugin)) {
          val pluginData = state.plugins[idString] ?: savePluginState(plugin)
          pluginData.isEnabled = plugin.isEnabled && !sessionUninstalledPlugins.contains(idString)
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
    pluginData.category = SettingsSyncPluginCategoryFinder.getPluginCategory(descriptor)
    for (dependency in descriptor.dependencies) {
      if (!dependency.isOptional) {
        pluginData.dependencies.add(dependency.pluginId.idString)
        pluginData.intIncrementModificationCount()
      }
    }
    val idString = descriptor.pluginId.idString
    state.plugins[idString] = pluginData
    LOG.info("Updated plugin state: ${idString}, enabled = ${pluginData.isEnabled}")
    return pluginData
  }

  /**
   * Makes the state of plugins in the running IDE match their state written in this component,
   * i.e. disables, enables and installs plugins according to the State.
   * It doesn't uninstall plugins - it only disable it.
   */
  fun pushChangesToIde() {
    val pluginManagerProxy = PluginManagerProxy.getInstance()
    val installer = pluginManagerProxy.createInstaller()

    val pluginsToDisable = mutableSetOf<PluginId>()
    val pluginsToEnable = mutableSetOf<PluginId>()
    val pluginsToInstall = mutableListOf<PluginId>()

    synchronized(LOCK) {
      for ((pluginId, pluginData) in state.plugins) {
        val plugin = findPlugin(pluginId)
        if (plugin != null) {
          if (isPluginSyncEnabled(plugin)) {
            if (pluginData.isEnabled != isPluginEnabled(plugin.pluginId)) {
              if (pluginData.isEnabled) {
                pluginsToEnable += plugin.pluginId
              }
              else {
                pluginsToDisable += plugin.pluginId
              }
            }
          }
        }
        else {
          // we don't know this plugin => it should be installed (unless it is disabled in the state and if installation is possible)
          if (pluginData.isEnabled &&
              isPluginSyncEnabled(pluginId, false, pluginData.category) &&
              checkDependencies(pluginId, pluginData)) {
            pluginsToInstall += PluginId.getId(pluginId)
          }
        }
      }
    }

    runInEdt {
      LOG.info("Enabling plugins: $pluginsToEnable")
      pluginManagerProxy.enablePlugins(pluginsToEnable)
    }

    runInEdt {
      LOG.info("Disabling plugins: $pluginsToDisable")
      pluginManagerProxy.disablePlugins(pluginsToDisable)
    }

    LOG.info("Installing plugins: $pluginsToInstall")
    installer.installPlugins(pluginsToInstall)
  }

  internal fun updateStateFromFileStateContent(pluginsFileState: FileState) {
    val stateFromFile = if (pluginsFileState is FileState.Modified) {
      val element = JDOMUtil.load(pluginsFileState.content)
      jdomSerializer.deserialize(element, SyncPluginsState::class.java)
    }
    else {
      SyncPluginsState()
    }

    synchronized(LOCK) {
      state = stateFromFile
    }
  }

  private fun isPluginEnabled(pluginId: PluginId) = !DisabledPluginsState.getDisabledIds().contains(pluginId)

  private fun findPlugin(idString: String): IdeaPluginDescriptor? {
    return PluginId.findId(idString)?.let { PluginManagerProxy.getInstance().findPlugin(it) }
  }

  private fun checkDependencies(idString: String, pluginState: PluginData): Boolean {
    for (dependency in pluginState.dependencies) {
      if (findPlugin(dependency) == null) {
        LOG.info("Skipping ${idString} plugin installation due to missing dependency: ${dependency}")
        return false
      }
    }
    return true
  }

  private fun shouldSaveState(plugin: IdeaPluginDescriptor): Boolean {
    return isPluginSyncEnabled(plugin) &&
           (!plugin.isBundled || !plugin.isEnabled || state.plugins.containsKey(plugin.pluginId.idString))
  }

  private fun isPluginSyncEnabled(plugin: IdeaPluginDescriptor) =
    isPluginSyncEnabled(plugin.pluginId.idString, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin))

  private fun isPluginSyncEnabled(idString: String, isBundled: Boolean, category: SettingsCategory): Boolean {
    val settings = SettingsSyncSettings.getInstance()
    return settings.isCategoryEnabled(category) &&
           (category != SettingsCategory.PLUGINS ||
            isBundled && settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID) ||
            settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, idString))
  }

  override fun dispose() {
    PluginStateManager.removeStateListener(pluginInstallationStateListener)
  }

  @TestOnly
  internal fun getPluginStateListener(): PluginStateListener = pluginInstallationStateListener

  private inner class PluginInstallationStateListener : PluginStateListener {
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

  private inner class PluginEnabledStateListener : Runnable {
    override fun run() {
      val disabledIds = PluginManagerProxy.getInstance().getDisabledPluginIds()
      val disabledIdStrings = HashSet<String>()
      synchronized(LOCK) {
        for (disabledPluginId in disabledIds) {
          LOG.info("Plugin ${disabledPluginId.idString} is disabled.")
          disabledIdStrings.add(disabledPluginId.idString)
          if (!state.plugins.containsKey(disabledPluginId.idString)) {
            PluginManagerProxy.getInstance().findPlugin(disabledPluginId)?.let { descriptor ->
              if (isPluginSyncEnabled(descriptor)) {
                savePluginState(descriptor)
              }
            }
          }
        }
        val pluginsToRemove = ArrayList<String>()
        for ((id, pluginData) in state.plugins) {
          val pluginId = PluginId.getId(id)
          PluginManagerProxy.getInstance().findPlugin(pluginId)?.let {
            if (disabledIdStrings.contains(id)) {
              pluginData.isEnabled = false
              LOG.info("${pluginId.idString} state changed to disabled")
            }
            else {
              if (it.isBundled) {
                pluginsToRemove.add(id)
              }
              else {
                pluginData.isEnabled = true
                LOG.info("${pluginId.idString} state changed to enabled")
              }
            }
          }
        }
        for (pluginToRemove in pluginsToRemove) {
          state.plugins.remove(pluginToRemove)
          LOG.info("Removed plugin data for ${pluginToRemove}")
        }
      }
    }
  }

  internal companion object {
    fun getInstance(): SettingsSyncPluginManager = ApplicationManager.getApplication().getService(SettingsSyncPluginManager::class.java)
    const val FILE_SPEC = "settingsSyncPlugins.xml"
    private val LOG = logger<SettingsSyncPluginManager>()
  }
}