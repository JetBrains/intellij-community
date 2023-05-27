package com.intellij.settingsSync

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.plugins.*
import org.junit.Assert
import java.util.concurrent.CopyOnWriteArrayList

internal class TestPluginManager : AbstractPluginManagerProxy() {
  val installer = TestPluginInstaller {
    addPluginDescriptors(TestPluginDescriptor.ALL[it]!!)
  }
  private val ownPluginDescriptors = HashMap<PluginId, IdeaPluginDescriptor>()
  private val pluginEnabledStateListeners = CopyOnWriteArrayList<Runnable>()
  var pluginStateExceptionThrower: ((PluginId) -> Unit)? = null

  override fun getPlugins(): Array<IdeaPluginDescriptor> {
    return ownPluginDescriptors.values.toTypedArray()
  }

  override val pluginEnabler: PluginEnabler
    get() = object : PluginEnabler {
      override fun enableById(pluginIds: MutableSet<PluginId>): Boolean {
        for (plugin in pluginIds) {
          val descriptor = findPlugin(plugin)
          assert(descriptor is TestPluginDescriptor)
          descriptor?.isEnabled = true
          pluginStateExceptionThrower?.invoke(plugin)
        }
        for (pluginListener in pluginEnabledStateListeners) {
          pluginListener.run()
        }
        return true
      }

      override fun disableById(pluginIds: MutableSet<PluginId>): Boolean {
        for (plugin in pluginIds) {
          val descriptor = findPlugin(plugin)
          assert(descriptor is TestPluginDescriptor)
          descriptor?.isEnabled = false
          pluginStateExceptionThrower?.invoke(plugin)
        }
        for (pluginListener in pluginEnabledStateListeners) {
          pluginListener.run()
        }
        return true
      }

      override fun isDisabled(pluginId: PluginId): Boolean = throw UnsupportedOperationException()
      override fun enable(descriptors: MutableCollection<out IdeaPluginDescriptor>): Boolean = throw UnsupportedOperationException()
      override fun disable(descriptors: MutableCollection<out IdeaPluginDescriptor>): Boolean = throw UnsupportedOperationException()
    }

  override fun isDescriptorEssential(pluginId: PluginId): Boolean {
    val descriptor = ownPluginDescriptors[pluginId] ?: Assert.fail("Cannot find descriptor for pluginId $pluginId")
    return (descriptor as TestPluginDescriptor).isEssential()

  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return ownPluginDescriptors.filter { (_, descriptor) -> !descriptor.isEnabled }.keys
  }

  override fun addDisablePluginListener(disabledListener: Runnable, parentDisposable: Disposable) {
    pluginEnabledStateListeners.add(disabledListener)
    Disposer.register(parentDisposable, Disposable {
      pluginEnabledStateListeners.remove(disabledListener)
      pluginStateExceptionThrower = null
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

  fun addPluginDescriptors(vararg descriptors: IdeaPluginDescriptor) {
    for (descriptor in descriptors) {
      ownPluginDescriptors[descriptor.pluginId] = descriptor
    }
  }

  fun disablePlugin(pluginId: PluginId) {
    disablePlugins(setOf(pluginId))
  }

  fun enablePlugin(pluginId: PluginId) {
    enablePlugins(setOf(pluginId))
  }
}