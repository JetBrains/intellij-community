package com.intellij.settingsSync

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.plugins.SettingsSyncPluginInstaller
import java.nio.file.Path
import java.util.*

class TestPluginInstaller(private val afterInstallPluginCallback: (PluginId) -> Unit) : SettingsSyncPluginInstaller {
  val installedPluginIds = HashSet<PluginId>()

  // there's no marketplace to find plugin descriptors, so we'll just populate that in advance

  internal fun justInstalledPlugins(): Collection<IdeaPluginDescriptor> =
    TestPluginDescriptor.ALL.filter { installedPluginIds.contains(it.key) }.values

  override fun installPlugins(pluginsToInstall: List<PluginId>) {
    for (pluginId in pluginsToInstall) {
      installedPluginIds += pluginId
      afterInstallPluginCallback.invoke(pluginId)
    }
  }
}

class TestPluginDependency(private val idString: String, override val isOptional: Boolean) : IdeaPluginDependency {
  override val pluginId
    get() = PluginId.getId(idString)

}

data class TestPluginDescriptor(
  val idString: String,
  var pluginDependencies: List<TestPluginDependency> = emptyList(),
  val bundled: Boolean = false,
  private var essential: Boolean = false
) : IdeaPluginDescriptor {
  companion object {
    val ALL = hashMapOf<PluginId, TestPluginDescriptor>()
  }

  private var _enabled = true
  private val _pluginId: PluginId

  init {
    _pluginId = PluginId.getId(idString)
    ALL[_pluginId] = this
  }

  override fun getPluginId(): PluginId = _pluginId

  override fun getPluginClassLoader() = classLoader

  override fun getPluginPath(): Path {
    throw UnsupportedOperationException("Not supported")
  }

  fun isEssential(): Boolean {
    return essential
  }

  override fun isBundled(): Boolean {
    return bundled
  }

  // region non-essential methods
  override fun getDescription(): String? = null
  override fun getChangeNotes(): String? = null
  override fun getName(): String = "Test plugin name"
  override fun getProductCode(): String? = null
  override fun getReleaseDate(): Date? = null
  override fun getReleaseVersion(): Int = 1
  override fun isLicenseOptional(): Boolean = true

  @Deprecated("Deprecated in Java")
  override fun getOptionalDependentPluginIds(): Array<PluginId> = PluginId.EMPTY_ARRAY
  override fun getVendor(): String? = null
  override fun getVersion(): String = "1.0"
  override fun getResourceBundleBaseName(): String? = null
  override fun getCategory(): String? = null
  override fun getVendorEmail(): String? = null
  override fun getVendorUrl(): String? = null
  override fun getUrl(): String? = null
  override fun getSinceBuild(): String? = null
  override fun getUntilBuild(): String? = null
  // endregion

  override fun isEnabled(): Boolean = _enabled

  override fun setEnabled(enabled: Boolean) {
    this._enabled = enabled
  }

  fun withEnabled(enabled: Boolean): TestPluginDescriptor {
    isEnabled = enabled
    return this
  }

  override fun getDependencies(): MutableList<IdeaPluginDependency> = pluginDependencies.toMutableList()

  override fun getDescriptorPath(): String? = null
}
