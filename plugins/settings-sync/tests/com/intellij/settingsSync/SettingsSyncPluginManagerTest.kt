package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import junit.framework.TestCase

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
  private lateinit var testPluginManager: TestPluginManager

  override fun setUp() {
    super.setUp()
    SettingsSyncSettings.getInstance().syncEnabled = true
    testPluginManager = TestPluginManager()
    ApplicationManager.getApplication().replaceService(PluginManagerProxy::class.java, testPluginManager, testRootDisposable)
  }

  override fun tearDown() {
    try {
      SettingsSyncPluginManager.getInstance().clearState()
    }
    finally {
      super.tearDown()
    }
  }

  fun `test install missing plugins`() {
    loadPluginManagerState(incomingPluginData)
    SettingsSyncPluginManager.getInstance().pushChangesToIDE()
    val installedPluginIds = testPluginManager.installer.installedPluginIds
    // Make sure QuickJump is skipped because it is disabled
    TestCase.assertEquals(2, installedPluginIds.size)
    TestCase.assertTrue(installedPluginIds.containsAll(
      listOf("codeflections.typengo", "color.scheme.IdeaLight")))
  }


  fun `test do not install when plugin sync is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, false)
    try {
      loadPluginManagerState(incomingPluginData)
      SettingsSyncPluginManager.getInstance().pushChangesToIDE()
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
      loadPluginManagerState(incomingPluginData)
      SettingsSyncPluginManager.getInstance().pushChangesToIDE()
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
    // Pretend QuickJump is installed
    val descriptor = TestPluginDescriptor(
      "QuickJump",
      listOf(TestPluginDependency("com.intellij.modules.platform", false))
    )
    testPluginManager.addPluginDescriptor(descriptor)
    TestCase.assertTrue(descriptor.isEnabled)
    loadPluginManagerState(incomingPluginData)
    SettingsSyncPluginManager.getInstance().pushChangesToIDE()
    TestCase.assertFalse(descriptor.isEnabled)
  }

  fun `test default state is empty` () {
    val state = SettingsSyncPluginManager.getInstance().state
    TestCase.assertTrue(state.plugins.isEmpty())
  }

  fun `test state contains installed plugin` () {
    val descriptor = TestPluginDescriptor(
      "QuickJump",
      listOf(TestPluginDependency("com.intellij.modules.platform", false))
    )
    testPluginManager.addPluginDescriptor(descriptor)
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
}