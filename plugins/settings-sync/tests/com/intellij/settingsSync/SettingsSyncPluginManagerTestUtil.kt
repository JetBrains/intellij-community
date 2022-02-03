package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginInstaller
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.util.xmlb.XmlSerializer
import junit.framework.TestCase
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Path
import java.util.*

class TestPluginManager : PluginManagerProxy {
  val installer = TestPluginInstaller()
  private val ownPluginDescriptors = HashMap<PluginId, IdeaPluginDescriptor>()

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
  }
}

class TestPluginInstaller : SettingsSyncPluginInstaller {
  val installedPluginIds = HashSet<String>()

  override fun addPluginId(pluginId: PluginId) {
    installedPluginIds.add(pluginId.idString)
  }

  override fun startInstallation() {
  }
}

class TestPluginDependency(private val idString: String, override val isOptional: Boolean) : IdeaPluginDependency {
  override val pluginId
    get() = PluginId.getId(idString)

}

class TestPluginDescriptor(idString: String, dependencies: List<TestPluginDependency>?) : IdeaPluginDescriptor {
  private var _enabled = true
  private val _pluginId = PluginId.getId(idString)
  private val _dependencies = dependencies ?: Collections.emptyList()

  override fun getPluginId(): PluginId = _pluginId

  override fun getPluginClassLoader() = classLoader

  override fun getPluginPath(): Path {
    throw UnsupportedOperationException("Not supported")
  }

  // region non-essential methods
  override fun getDescription(): String? = null
  override fun getChangeNotes(): String? = null
  override fun getName(): String = "Test plugin name"
  override fun getProductCode(): String? = null
  override fun getReleaseDate(): Date? = null
  override fun getReleaseVersion(): Int = 1
  override fun isLicenseOptional(): Boolean = true
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
    _enabled = enabled
  }

  override fun getDependencies(): MutableList<IdeaPluginDependency> = _dependencies.toMutableList()

  override fun getDescriptorPath(): String? = null
}

private fun loadElement(xmlData: String): Element {
  val builder = SAXBuilder()
  val doc = builder.build(StringReader(xmlData))
  return doc.rootElement
}

fun loadPluginManagerState(xmlData: String) {
  val element = loadElement(xmlData)
  ComponentSerializationUtil.loadComponentState(SettingsSyncPluginManager.getInstance(), element)
}

fun assertSerializedStateEquals(expected: String) {
  val state = SettingsSyncPluginManager.getInstance().state
  val e = Element("component")
  XmlSerializer.serializeInto(state, e)
  val writer = StringWriter()
  val format = Format.getPrettyFormat()
  format.lineSeparator = "\n"
  XMLOutputter(format).output(e, writer)
  val actual = writer.toString()
  TestCase.assertEquals(expected, actual)
}