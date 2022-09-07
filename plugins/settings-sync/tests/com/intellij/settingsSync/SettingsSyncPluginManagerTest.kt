package com.intellij.settingsSync

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.io.readText
import com.intellij.util.xmlb.XmlSerializer
import junit.framework.TestCase
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringWriter

// region Test data
private const val incomingPluginData = """<component name="SettingsSyncPlugins">
  <option name="plugins">
    <map>
      <entry key="QuickJump">
        <value>
          <PluginData>
            <option name="dependencies">
              <set>
                <option value="com.intellij.modules.platform" />
              </set>
            </option>
            <option name="enabled" value="false" />
          </PluginData>
        </value>
      </entry>
      <entry key="codeflections.typengo">
        <value>
          <PluginData>
            <option name="dependencies">
              <set>
                <option value="com.intellij.modules.platform" />
              </set>
            </option>
          </PluginData>
        </value>
      </entry>
      <entry key="color.scheme.IdeaLight">
        <value>
          <PluginData>
            <option name="category" value="UI" />
            <option name="dependencies">
              <set>
                <option value="com.intellij.modules.lang" />
              </set>
            </option>
          </PluginData>
        </value>
      </entry>
      <entry key="com.company.plugin">
        <value>
          <PluginData>
            <option name="dependencies">
             <set>
                <option value="com.intellij.modules.lang" />
                <option value="com.company.other.plugin" />
              </set>
            </option>
          </PluginData>
        </value>
      </entry>
    </map>
  </option>
</component>"""

// endregion

class SettingsSyncPluginManagerTest : LightPlatformTestCase() {
  private lateinit var pluginManager: SettingsSyncPluginManager
  private lateinit var testPluginManager: TestPluginManager

  private val quickJump = TestPluginDescriptor(
    "QuickJump",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )
  private val typengo = TestPluginDescriptor(
    "codeflections.typengo",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )

  override fun setUp() {
    super.setUp()
    SettingsSyncSettings.getInstance().syncEnabled = true
    testPluginManager = TestPluginManager()
    ApplicationManager.getApplication().replaceService(PluginManagerProxy::class.java, testPluginManager, testRootDisposable)
    pluginManager = SettingsSyncPluginManager()
    Disposer.register(testRootDisposable, pluginManager)
  }

  fun `test install missing plugins`() {
    pluginManager.updateStateFromFileStateContent(getTestDataFileState())
    pluginManager.pushChangesToIde()

    val installedPluginIds = testPluginManager.installer.installedPluginIds
    // Make sure QuickJump is skipped because it is disabled
    TestCase.assertEquals(2, installedPluginIds.size)
    TestCase.assertTrue(installedPluginIds.containsAll(
      listOf("codeflections.typengo", "color.scheme.IdeaLight")))
  }

  private fun getTestDataFileState(): FileState.Modified {
    val content = incomingPluginData.toByteArray()
    return FileState.Modified(SettingsSyncPluginManager.FILE_SPEC, content, content.size)
  }

  fun `test do not install when plugin sync is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, false)
    try {
      pluginManager.updateStateFromFileStateContent(getTestDataFileState())
      pluginManager.pushChangesToIde()
      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      TestCase.assertEquals(1, installedPluginIds.size)
      TestCase.assertTrue(installedPluginIds.contains("color.scheme.IdeaLight"))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, true)
    }
  }

  fun `test do not install UI plugin when UI category is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, false)
    try {
      pluginManager.updateStateFromFileStateContent(getTestDataFileState())
      pluginManager.pushChangesToIde()
      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      TestCase.assertEquals(1, installedPluginIds.size)
      TestCase.assertTrue(installedPluginIds.contains("codeflections.typengo"))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, true)
    }
  }

  fun `test disable installed plugin`() {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump)
    pluginManager.updateStateFromFileStateContent(getTestDataFileState())
    assertTrue(quickJump.isEnabled)
    pluginManager.pushChangesToIde()
    assertFalse(quickJump.isEnabled)
  }

  fun `test default state is empty` () {
    val state = pluginManager.state
    TestCase.assertTrue(state.plugins.isEmpty())
  }

  fun `test state contains installed plugin` () {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump)
    assertSerializedStateEquals(
      """
      <component>
        <option name="plugins">
          <map>
            <entry key="QuickJump">
              <value>
                <PluginData>
                  <option name="dependencies">
                    <set>
                      <option value="com.intellij.modules.platform" />
                    </set>
                  </option>
                </PluginData>
              </value>
            </entry>
          </map>
        </option>
      </component>""".trimIndent())
  }

  fun `test disable two plugins at once`() {
    // install two plugins
    testPluginManager.addPluginDescriptors(pluginManager, quickJump, typengo)

    pluginManager.updateStateFromFileStateContent(getTestDataFileState())
    pluginManager.state.plugins["codeflections.typengo"] = SettingsSyncPluginManager.PluginData().apply {
      this.isEnabled = false
    }

    TestCase.assertTrue(quickJump.isEnabled)
    TestCase.assertTrue(typengo.isEnabled)

    pluginManager.pushChangesToIde()

    TestCase.assertFalse(quickJump.isEnabled)
    TestCase.assertFalse(typengo.isEnabled)
  }

  fun `test plugin manager collects state on start`() {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump)

    val element = JDOMUtil.load(incomingPluginData)
    ComponentSerializationUtil.loadComponentState(pluginManager, element)

    val dataForQuickJump = pluginManager.state.plugins[quickJump.idString]
    assertNotNull("The data about ${quickJump.idString} plugin is not in the state", dataForQuickJump)
    TestCase.assertTrue("Plugin is enabled in the IDE but disabled in the state", dataForQuickJump!!.isEnabled)
  }

  fun `test state deserialization`() {
    pluginManager.state.plugins["A"] = SettingsSyncPluginManager.PluginData().apply { this.isEnabled = false }
    (ApplicationManager.getApplication() as ComponentManagerImpl).componentStore.saveComponent(pluginManager)
    val serializedComponent = PathManager.getConfigDir().resolve("options").resolve("settingsSyncPlugins.xml").readText()
    pluginManager.state.plugins.clear()

    val byteArray = serializedComponent.toByteArray()
    pluginManager.updateStateFromFileStateContent(FileState.Modified("options/settingsSyncPlugins.xml", byteArray, byteArray.size))

    val pluginData = pluginManager.state.plugins["A"]
    assertNotNull("Plugin data for A is not found in ${pluginManager.state.plugins}", pluginData)
    TestCase.assertFalse(pluginData!!.isEnabled)
  }

  private fun assertSerializedStateEquals(expected: String) {
    val state = pluginManager.state
    val e = Element("component")
    XmlSerializer.serializeInto(state, e)
    val writer = StringWriter()
    val format = Format.getPrettyFormat()
    format.lineSeparator = "\n"
    XMLOutputter(format).output(e, writer)
    val actual = writer.toString()
    TestCase.assertEquals(expected, actual)
  }
}