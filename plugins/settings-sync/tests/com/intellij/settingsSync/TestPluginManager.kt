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
  private val disabledPluginListeners = CopyOnWriteArrayList<Runnable>()

  override fun getPlugins(): Array<IdeaPluginDescriptor> {
    val descriptors = arrayListOf<IdeaPluginDescriptor>()
    descriptors.addAll(PluginManagerCore.getPlugins())
    descriptors.addAll(ownPluginDescriptors.values)
    return descriptors.toTypedArray()
  }

  override fun enablePlugin(pluginId: PluginId) {
    val descriptor = findPlugin(pluginId)
    assert(descriptor is TestPluginDescriptor)
    descriptor?.isEnabled = true
  }

  override fun disablePlugin(pluginId: PluginId) {
    val descriptor = findPlugin(pluginId)
    assert(descriptor is TestPluginDescriptor)
    descriptor?.isEnabled = false
    for (disabledPluginListener in disabledPluginListeners) {
      disabledPluginListener.run()
    }
  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return ownPluginDescriptors.filter { (_, descriptor) -> !descriptor.isEnabled }.keys
  }

  override fun addDisablePluginListener(disabledListener: Runnable, parentDisposable: Disposable) {
    disabledPluginListeners.add(disabledListener)
    Disposer.register(parentDisposable, Disposable {
      disabledPluginListeners.remove(disabledListener)
    })
  }

  override fun findPlugin(pluginId: PluginId): IdeaPluginDescriptor? {
    return if (ownPluginDescriptors.containsKey(pluginId)) {
      ownPluginDescriptors[pluginId]
    }
    else PluginManagerCore.findPlugin(pluginId)
  }

  override fun createInstaller(): SettingsSyncPluginInstaller {
    return installer
  }

  fun addPluginDescriptor(descriptor: IdeaPluginDescriptor) {
    ownPluginDescriptors[descriptor.pluginId] = descriptor
    SettingsSyncPluginManager.getInstance().getPluginStateListener().install(descriptor)
  }
}