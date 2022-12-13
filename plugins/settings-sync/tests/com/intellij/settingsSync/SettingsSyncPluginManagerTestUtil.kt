package com.intellij.settingsSync

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path
import java.util.*

class TestPluginDependency(private val idString: String, override val isOptional: Boolean) : IdeaPluginDependency {
  override val pluginId
    get() = PluginId.getId(idString)

}

data class TestPluginDescriptor(
  val idString: String,
  var pluginDependencies: List<TestPluginDependency> = emptyList(),
  val bundled: Boolean = false,
  val pluginCategory: String? = null) : IdeaPluginDescriptor
{
  private var _enabled = true
  private val _pluginId = PluginId.getId(idString)

  override fun getPluginId(): PluginId = _pluginId

  override fun getPluginClassLoader() = classLoader

  override fun getPluginPath(): Path {
    throw UnsupportedOperationException("Not supported")
  }

  override fun isBundled(): Boolean {
    return bundled
  }

  override fun getCategory(): String? {
    return pluginCategory
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
  override fun getVendorEmail(): String? = null
  override fun getVendorUrl(): String? = null
  override fun getUrl(): String? = null
  override fun getSinceBuild(): String? = null
  override fun getUntilBuild(): String? = null
  // endregion

  override fun isEnabled(): Boolean = _enabled

  override fun setEnabled(enabled: Boolean) {
    _enabled = enabled
  }

  override fun getDependencies(): MutableList<IdeaPluginDependency> = pluginDependencies.toMutableList()

  override fun getDescriptorPath(): String? = null
}
