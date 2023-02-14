package com.intellij.settingsSync

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginInstaller
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import java.util.concurrent.CopyOnWriteArrayList

internal class TestPluginManager : PluginManagerProxy {
  val installer = TestPluginInstaller()
  private val ownPluginDescriptors = HashMap<PluginId, IdeaPluginDescriptor>()
  private val pluginEnabledStateListeners = CopyOnWriteArrayList<Runnable>()

  override fun getPlugins(): Array<IdeaPluginDescriptor> {
    return ownPluginDescriptors.values.toTypedArray()
  }

  override fun enablePlugins(plugins: Set<PluginId>) {
    for (plugin in plugins) {
      val descriptor = findPlugin(plugin)
      assert(descriptor is TestPluginDescriptor)
      descriptor?.isEnabled = true
    }
    for (pluginListener in pluginEnabledStateListeners) {
      pluginListener.run()
    }
  }

  override fun disablePlugins(plugins: Set<PluginId>) {
    for (plugin in plugins) {
      val descriptor = findPlugin(plugin)
      assert(descriptor is TestPluginDescriptor)
      descriptor?.isEnabled = false
    }
    for (pluginListener in pluginEnabledStateListeners) {
      pluginListener.run()
    }
  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return ownPluginDescriptors.filter { (_, descriptor) -> !descriptor.isEnabled }.keys
  }

  override fun addDisablePluginListener(disabledListener: Runnable, parentDisposable: Disposable) {
    pluginEnabledStateListeners.add(disabledListener)
    Disposer.register(parentDisposable, Disposable {
      pluginEnabledStateListeners.remove(disabledListener)
    })
  }

  override fun findPlugin(pluginId: PluginId): IdeaPluginDescriptor? {
    return if (ownPluginDescriptors.containsKey(pluginId)) {
      ownPluginDescriptors[pluginId]
    }
    else PluginManagerCore.findPlugin(pluginId)
  }

  override fun createInstaller(notifyErrors: Boolean): SettingsSyncPluginInstaller {
    return installer
  }

  fun addPluginDescriptors(pluginManager: SettingsSyncPluginManager, vararg descriptors: IdeaPluginDescriptor) {
    for (descriptor in descriptors) {
      ownPluginDescriptors[descriptor.pluginId] = descriptor
      pluginManager.getPluginStateListener().install(descriptor)
    }
  }

  fun disablePlugin(pluginId: PluginId) {
    disablePlugins(setOf(pluginId))
  }

  fun enablePlugin(pluginId: PluginId) {
    enablePlugins(setOf(pluginId))
  }
}