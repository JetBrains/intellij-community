package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.*
import com.intellij.settingsSync.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState.PluginData
import org.jetbrains.annotations.TestOnly
import java.time.Instant

internal class SettingsSyncPluginManager : Disposable {

  private val pluginInstallationStateListener = PluginInstallationStateListener()
  private val pluginEnabledStateListener = PluginEnabledStateListener()
  private val LOCK = Object()

  internal var state = SettingsSyncPluginsState(emptyMap())
    private set

  private val sessionUninstalledPlugins = HashSet<String>()

  init {
    PluginStateManager.addStateListener(pluginInstallationStateListener)
    PluginManagerProxy.getInstance().addDisablePluginListener(pluginEnabledStateListener, this)
  }

  internal fun updateStateFromIdeOnStart(lastSavedPluginsState: SettingsSyncPluginsState?): SettingsSyncPluginsState {
    synchronized(LOCK) {
      val oldPlugins = lastSavedPluginsState?.plugins ?: emptyMap()
      val newPlugins = oldPlugins.toMutableMap()

      for (plugin in PluginManagerProxy.getInstance().getPlugins()) {
        val id = plugin.pluginId
        if (shouldSaveState(plugin)) {
          newPlugins[id] = getPluginData(plugin)
        }
        else {
          newPlugins -= id
        }
      }

      logChangedState("Updated component state by the state of IDE.", oldPlugins, newPlugins)
      state = SettingsSyncPluginsState(newPlugins)
      return state
    }
  }

  private fun firePluginsStateChangeEvent(pluginsState: SettingsSyncPluginsState) {
    val snapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo()), emptySet(), pluginsState)
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
  }

  private fun logChangedState(message: String, oldPlugins: Map<PluginId, PluginData>, newPlugins: Map<PluginId, PluginData>) {
    val pluginsWithEnabledStateChanged = newPlugins.filter { (newKey, newData) ->
      val oldData = oldPlugins[newKey]
      oldData != null && oldData.enabled != newData.enabled
    }
    val pluginsWithNoChanges = newPlugins.filter {
      (newKey, newData) -> oldPlugins[newKey] == newData
    }
    val pluginsWithOtherChanges = newPlugins.filter { (newKey, newData) ->
      val oldData = oldPlugins[newKey]
      oldData != null && oldData != newData && oldData.enabled == newData.enabled
    }
    val enabledStateChanges = pluginsWithEnabledStateChanged.map { (id, _) ->
      "$id: ${enabledOrDisabled(oldPlugins[id]?.enabled)} -> ${enabledOrDisabled(newPlugins[id]?.enabled)}"
    }

    val addedPlugins = newPlugins.keys - oldPlugins.keys
    val removedPlugins = oldPlugins.keys - newPlugins.keys
    LOG.info("$message\n" +
             getLineIfNotEmpty("Added", addedPlugins) +
             getLineIfNotEmpty("Removed", removedPlugins) +
             getLineIfNotEmpty("Changed enabled state", enabledStateChanges) +
             getLineIfNotEmpty("No changes", pluginsWithNoChanges) +
             getLineIfNotEmpty("Other changes", pluginsWithOtherChanges))
  }

  private fun getLineIfNotEmpty(prefix: String, plugins: Collection<*>) = if (plugins.isNotEmpty()) "$prefix: $plugins\n" else ""
  private fun getLineIfNotEmpty(prefix: String, plugins: Map<*, *>) = if (plugins.isNotEmpty()) "$prefix: $plugins\n" else ""
  private fun enabledOrDisabled(value: Boolean?) = if (value == null) "null" else if (value) "enabled" else "disabled"

  private fun getPluginData(plugin: IdeaPluginDescriptor, explicitEnabled: Boolean? = null): PluginData {
    val isEnabled = if (explicitEnabled != null) {
      explicitEnabled
    }
    else {
      plugin.isEnabled && !sessionUninstalledPlugins.contains(plugin.pluginId.idString)
    }

    val dependencies = plugin.dependencies.filter { !it.isOptional }.map { it.pluginId.idString }.toSet()
    return PluginData(isEnabled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin), dependencies)
  }

  /**
   * Makes the state of plugins in the running IDE match their state written in this component,
   * i.e. disables, enables and installs plugins according to the State.
   * It doesn't uninstall plugins - it only disable it.
   */
  fun pushChangesToIde(newState: SettingsSyncPluginsState) {
    val pluginsToDisable = mutableSetOf<PluginId>()
    val pluginsToEnable = mutableSetOf<PluginId>()
    val pluginsToInstall = mutableListOf<PluginId>()

    synchronized(LOCK) {
      val oldPlugins = state.plugins
      val newPlugins = newState.plugins

      logChangedState("Pushed new changes to the IDE", oldPlugins, newPlugins)
      state = SettingsSyncPluginsState(newPlugins)

      val removedPluginData = oldPlugins.keys - newPlugins.keys
      for (id in removedPluginData) {
        // normally, only a bundled plugin which was enabled back (i.e. moved to its default state) can be removed from the state
        val plugin = PluginManagerProxy.getInstance().findPlugin(id)
        if (plugin != null && isPluginSyncEnabled(plugin)) {
          if (plugin.isBundled && !plugin.isEnabled) {
            pluginsToEnable += plugin.pluginId
          }
        }
      }

      for ((pluginId, pluginData) in newPlugins) {
        val plugin = PluginManagerProxy.getInstance().findPlugin(pluginId)
        if (plugin != null) {
          if (isPluginSyncEnabled(plugin)) {
            val oldData = oldPlugins[pluginId]
            // if enabled state has changed, or if there were no information about this plugin,
            // then we enable/disable the plugin according to the new state
            if (oldData == null || pluginData.enabled != oldData.enabled) {
              if (pluginData.enabled) {
                pluginsToEnable += pluginId
              }
              else {
                pluginsToDisable += pluginId
              }
            }
          }
        }
        else { // we don't know this plugin => it should be installed (unless it is disabled in the state and if installation is possible)
          if (pluginData.enabled &&
              isPluginSyncEnabled(pluginId, isBundled = false, pluginData.category) &&
              checkDependencies(pluginId, pluginData)) {
            pluginsToInstall += pluginId
          }

          if (oldPlugins.containsKey(pluginId)) { // this normally shouldn't happen, so let's log
            LOG.warn("Plugin $pluginId was in the state but not installed")
          }
        }
      }
    }

    val pluginManagerProxy = PluginManagerProxy.getInstance()

    invokeAndWaitIfNeeded {
      LOG.info("Enabling plugins: $pluginsToEnable")
      pluginManagerProxy.enablePlugins(pluginsToEnable)
    }

    invokeAndWaitIfNeeded {
      LOG.info("Disabling plugins: $pluginsToDisable")
      pluginManagerProxy.disablePlugins(pluginsToDisable)
    }

    LOG.info("Installing plugins: $pluginsToInstall")
    pluginManagerProxy.createInstaller().installPlugins(pluginsToInstall)
  }

  private fun isPluginEnabled(pluginId: PluginId) = !DisabledPluginsState.getDisabledIds().contains(pluginId)

  private fun findPlugin(idString: String): IdeaPluginDescriptor? {
    return PluginId.findId(idString)?.let { PluginManagerProxy.getInstance().findPlugin(it) }
  }

  private fun checkDependencies(id: PluginId, pluginState: PluginData): Boolean {
    for (dependency in pluginState.dependencies) {
      if (findPlugin(dependency) == null) {
        LOG.info("Skipping ${id} plugin installation due to missing dependency: ${dependency}")
        return false
      }
    }
    return true
  }

  private fun shouldSaveState(plugin: IdeaPluginDescriptor): Boolean {
    return isPluginSyncEnabled(plugin) && (!plugin.isBundled || !plugin.isEnabled)
  }

  private fun isPluginSyncEnabled(plugin: IdeaPluginDescriptor) =
    isPluginSyncEnabled(plugin.pluginId, plugin.isBundled, SettingsSyncPluginCategoryFinder.getPluginCategory(plugin))

  private fun isPluginSyncEnabled(id: PluginId, isBundled: Boolean, category: SettingsCategory): Boolean {
    val settings = SettingsSyncSettings.getInstance()
    return settings.isCategoryEnabled(category) &&
           (category != SettingsCategory.PLUGINS ||
            isBundled && settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID) ||
            settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, id.idString))
  }

  override fun dispose() {
    PluginStateManager.removeStateListener(pluginInstallationStateListener)
  }

  @TestOnly
  internal fun getPluginStateListener(): PluginStateListener = pluginInstallationStateListener

  private inner class PluginInstallationStateListener : PluginStateListener {
    override fun install(descriptor: IdeaPluginDescriptor) {
      val pluginId = descriptor.pluginId
      LOG.info("Installed plugin ${pluginId.idString}")
      synchronized(LOCK) {
        sessionUninstalledPlugins.remove(pluginId.idString)
        if (shouldSaveState(descriptor)) {
          val oldPlugins = state.plugins
          val newPlugins = oldPlugins + (pluginId to getPluginData(descriptor))
          state = SettingsSyncPluginsState(newPlugins)
          firePluginsStateChangeEvent(state)
        }
      }
    }

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
      val pluginId = descriptor.pluginId
      LOG.info("Uninstalled plugin $pluginId")
      synchronized(LOCK) {
        sessionUninstalledPlugins.add(pluginId.idString)
        if (shouldSaveState(descriptor)) {
          val oldPlugins = state.plugins
          val newPlugins = oldPlugins + (pluginId to getPluginData(descriptor))
          state = SettingsSyncPluginsState(newPlugins)
          firePluginsStateChangeEvent(state)
        }
      }
    }
  }

  private inner class PluginEnabledStateListener : Runnable {
    // this listener is called when some plugin or plugins change their enabled state
    override fun run() {
      val oldPlugins = state.plugins
      val newPlugins = oldPlugins.toMutableMap()
      val disabledIds = PluginManagerProxy.getInstance().getDisabledPluginIds()
      synchronized(LOCK) {
        for (disabledPluginId in disabledIds) {
          LOG.info("Plugin ${disabledPluginId.idString} is disabled.")
          if (!newPlugins.containsKey(disabledPluginId)) { // otherwise we'll handle this known plugin below while iterating through oldPlugins
            val disabledPlugin = PluginManagerProxy.getInstance().findPlugin(disabledPluginId)
            if (disabledPlugin != null) {
              newPlugins[disabledPluginId] = getPluginData(disabledPlugin, explicitEnabled = false)
            }
          }
        }

        for (id in oldPlugins.keys) {
          val plugin = PluginManagerProxy.getInstance().findPlugin(id)
          // iterate through all installed plugins and update their state according to the known disabled plugin ids
          // if plugin is not installed, we don't modify its state:
          // if it got uninstalled, the separate PluginInstallationStateListener will handle it or already has handled it.
          if (plugin != null && isPluginSyncEnabled(plugin)) {
            if (disabledIds.contains(id)) {
              newPlugins[id] = getPluginData(plugin, explicitEnabled = false)
              LOG.info("Plugin '$id' changed state to disabled")
            }
            else {
              if (plugin.isBundled) { // enabled bundled plugin is default => don't store in state
                newPlugins.remove(id)
                LOG.info("Removed plugin data for '$id'")
              }
              else {
                newPlugins[id] = getPluginData(plugin, explicitEnabled = true)
                LOG.info("Plugin '$id' changed state to enabled")
              }
            }
          }
        }

        state = SettingsSyncPluginsState(newPlugins.toMap())
        if (oldPlugins != newPlugins) {
          firePluginsStateChangeEvent(state)
        }
      }
    }
  }

  internal companion object {
    fun getInstance(): SettingsSyncPluginManager = ApplicationManager.getApplication().getService(SettingsSyncPluginManager::class.java)
    private val LOG = logger<SettingsSyncPluginManager>()
  }
}