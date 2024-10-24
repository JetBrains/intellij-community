package com.intellij.settingsSync

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.settingsSync.plugins.SettingsSyncPluginInstaller
import com.intellij.settingsSync.plugins.SettingsSyncPluginInstallerImpl
import java.nio.file.Path
import java.util.*

internal class TestPluginInstaller(private val afterInstallPluginCallback: (PluginId) -> Unit) : SettingsSyncPluginInstallerImpl(true) {
  val installedPluginIds = HashSet<PluginId>()
  var installPluginExceptionThrower: ((PluginId) -> Unit)? = null

  // there's no marketplace to find plugin descriptors, so we'll just populate that in advance

  override suspend fun installCollected(installers: List<PluginDownloader>, settingsAlreadyChanged: Boolean) {
    doInstallCollected(installers, settingsAlreadyChanged)
  }

  override suspend fun install(installer: PluginDownloader): Boolean {
    val pluginId = installer.id
    val descriptor = TestPluginDescriptor.ALL[pluginId] as TestPluginDescriptor
    if (!descriptor.isDynamic)
      return false
    installPluginExceptionThrower?.invoke(pluginId)
    installedPluginIds += pluginId
    afterInstallPluginCallback.invoke(pluginId)
    return true
  }

  override fun createDownloaders(pluginIds: Collection<PluginId>): List<PluginDownloader> {
    val retval = arrayListOf<PluginDownloader>()
    for (pluginId in pluginIds) {
      val descriptor = TestPluginDescriptor.ALL[pluginId] ?: continue
      retval.add(PluginDownloader.createDownloader(descriptor))
    }
    return retval
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
  val essential: Boolean = false,
  val compatible: Boolean = true,
  val isDependencyOnly: Boolean = false, // it's only a dependency, should be listed as a plugin
  val isDynamic: Boolean = true // whether can be enabled/disabled/installed without restart
) : IdeaPluginDescriptor {
  companion object {
    val ALL = hashMapOf<PluginId, TestPluginDescriptor>()

    fun allDependenciesOnly() : List<TestPluginDescriptor> =
      ALL.values.filter { it.isDependencyOnly }
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
