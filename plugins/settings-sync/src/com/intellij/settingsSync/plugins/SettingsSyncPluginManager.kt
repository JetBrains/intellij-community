package com.intellij.settingsSync.plugins

import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.*
import com.intellij.settingsSync.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState.PluginData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.time.Instant

internal class SettingsSyncPluginManager(private val cs: CoroutineScope) : Disposable {

  private val pluginInstallationStateListener = PluginInstallationStateListener()
  private val pluginEnabledStateListener = PluginEnabledStateListener()
  private val LOCK = Object()

  internal var state = SettingsSyncPluginsState(emptyMap())
    private set

  private val sessionUninstalledPlugins = HashSet<String>()

  init {
    PluginStateManager.addStateListener(pluginInstallationStateListener)
    PluginManagerProxy.getInstance().addPluginStateChangedListener(pluginEnabledStateListener, this)
  }

  internal fun updateStateFromIdeOnStart(lastSavedPluginsState: SettingsSyncPluginsState?): SettingsSyncPluginsState {
    synchronized(LOCK) {
      val currentIdePlugins = PluginManagerProxy.getInstance().getPlugins()
      val currentIdePluginIds = currentIdePlugins.map { it.pluginId }.toSet()

      val oldPlugins = lastSavedPluginsState?.plugins ?: emptyMap()
      val newPlugins = oldPlugins.toMutableMap()
      val removedPluginIds = newPlugins.keys - currentIdePluginIds
      if (removedPluginIds.isNotEmpty()) {
        LOG.info("Plugins ${removedPluginIds.joinToString()} have been deleted from disk. Will mark them as disabled in setting sync")
        for (pluginId in removedPluginIds) {
          newPlugins.computeIfPresent(pluginId) { _, data -> PluginData(enabled = false, data.category, data.dependencies) }
        }
      }

      for (plugin in currentIdePlugins) {
        val id = plugin.pluginId
        if (PluginManagerProxy.getInstance().isEssential(id)
            || PluginManagerProxy.getInstance().isIncompatible(plugin)) {
          // don't change state of essential plugin (it will be enabled in the current IDE anyway)
          // also, don't take into account incompatible plugins (makes no sense to deal with them)
          // other IDEs will manage such plugins themselves
        }
        else if (shouldSaveState(plugin)) {
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
    val snapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo()),
                                    emptySet(), pluginsState, emptyMap(), emptySet())
    SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.IdeChange(snapshot))
  }

  private fun logChangedState(message: String, oldPlugins: Map<PluginId, PluginData>, newPlugins: Map<PluginId, PluginData>) {
    val pluginsWithEnabledStateChanged = newPlugins.filter { (newKey, newData) ->
      val oldData = oldPlugins[newKey]
      oldData != null && oldData.enabled != newData.enabled
    }
    val pluginsWithNoChanges = newPlugins.filter { (newKey, newData) ->
      oldPlugins[newKey] == newData
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

    changePluginsStateAndReport(pluginsToDisable, false)
    changePluginsStateAndReport(pluginsToEnable, true)

    LOG.info("Installing plugins: $pluginsToInstall")
    PluginManagerProxy.getInstance().createInstaller().installPlugins(pluginsToInstall)
  }

  private fun changePluginsStateAndReport(plugins: Set<PluginId>, enable: Boolean) {
    if (plugins.isEmpty()) {
      return
    }

    invokeAndWaitIfNeeded {
      val actionName = if (enable) "enable" else "disable"
      try {
        LOG.info("Going to ${actionName} plugins: $plugins")
        val result = if (enable)
          PluginManagerProxy.getInstance().enablePlugins(plugins)
        else
          PluginManagerProxy.getInstance().disablePlugins(plugins)
        if (!result) {
          val pluginsReqRestart = mutableListOf<String>()
          for (pluginId in plugins) {
            val plugin = PluginManagerProxy.getInstance().findPlugin(pluginId) ?: continue
            if (plugin.isEnabled != enable) {
              pluginsReqRestart.add(plugin.name)
            }
          }
          LOG.warn("The $actionName for the following plugins require restart: " + pluginsReqRestart.joinToString())
          val message = if (enable) {
            SettingsSyncBundle.message("plugins.sync.enable.message",
                                       pluginsReqRestart.size)
          } else {
            SettingsSyncBundle.message("plugins.sync.disable.message",
                                       pluginsReqRestart.size)
          }
          SettingsSyncEvents.getInstance().fireRestartRequired(actionName, message)
        }
      }
      catch (ex: Exception) {
        LOG.warn("An exception occurred while $actionName plugins: $plugins", ex)
      }
    }
  }

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
    if (PluginManagerProxy.getInstance().isEssential(id))
      return false
    val settings = SettingsSyncSettings.getInstance()
    return settings.isCategoryEnabled(category) &&
           (category != SettingsCategory.PLUGINS ||
            isBundled && settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, BUNDLED_PLUGINS_ID) ||
            settings.isSubcategoryEnabled(SettingsCategory.PLUGINS, id.idString))
  }

  override fun dispose() {
    PluginStateManager.removeStateListener(pluginInstallationStateListener)
    cs.cancel()
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

  private inner class PluginEnabledStateListener : PluginEnableStateChangedListener {
    // this listener is called when some plugin or plugins change their enabled state

    private fun ed(b: Boolean) = if (b) "enable" else "disable"

    override fun stateChanged(pluginDescriptors: Collection<IdeaPluginDescriptor>, enable: Boolean) {
      cs.launch {
        synchronized(LOCK) {
          val oldPlugins = state.plugins
          val newPlugins = oldPlugins.toMutableMap()
          for (pluginDescriptor in pluginDescriptors) {
            val plugin = PluginManagerProxy.getInstance().findPlugin(pluginDescriptor.pluginId)
            if (plugin == null) {
              LOG.warn("got ${ed(enable)} info about non-existing plugin ${pluginDescriptor.pluginId}")
              continue
            }
            if (plugin.isEnabled != enable) {
              LOG.info("State of plugin ${pluginDescriptor.pluginId} is inconsistent: received ${ed(enable)} event, " +
                       "but plugin is ${ed(plugin.isEnabled)}d. Probably, a restart is required.")
            }
            if (plugin.isBundled && enable) {
              newPlugins.remove(pluginDescriptor.pluginId)
              LOG.info("Bundled plugin ${pluginDescriptor.pluginId} is ${ed(enable)}d. Will remove its info from plugins.json")
            }
            else {
              newPlugins[pluginDescriptor.pluginId] = getPluginData(pluginDescriptor, enable)
              LOG.info("${if (plugin.isBundled) "Bundled " else ""}Plugin ${pluginDescriptor.pluginId} is ${ed(enable)}d")
            }
          }
          if (oldPlugins != newPlugins) {
            state = SettingsSyncPluginsState(newPlugins.toMap())
            firePluginsStateChangeEvent(state)
          }
        }
      }
    }
  }

  internal companion object {
    fun getInstance(): SettingsSyncPluginManager = ApplicationManager.getApplication().getService(SettingsSyncPluginManager::class.java)
    private val LOG = logger<SettingsSyncPluginManager>()
  }
}