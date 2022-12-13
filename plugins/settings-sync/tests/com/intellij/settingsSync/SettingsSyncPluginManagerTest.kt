package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState.PluginData
import com.intellij.settingsSync.plugins.thisIde
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService

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
  private val ideaLight = TestPluginDescriptor(
    "color.scheme.IdeaLight",
    listOf(TestPluginDependency("com.intellij.modules.lang", isOptional = false)),
    pluginCategory = "Theme"
  )
  private val git4idea = TestPluginDescriptor(
    "git4idea",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
    bundled = true
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
    pluginManager.pushChangesToIde(state {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    })

    val installedPluginIds = testPluginManager.installer.installedPluginIds
    // NB: quickJump should be skipped because it is disabled
    assertEquals(2, installedPluginIds.size)
    assertTrue(installedPluginIds.containsAll(listOf(typengo.idString, ideaLight.idString)))
  }

  fun `test do not install when plugin sync is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, false)
    try {
      pluginManager.pushChangesToIde(state {
        quickJump(enabled = false)
        typengo(enabled = true)
        ideaLight(enabled = true, category = SettingsCategory.UI)
      })

      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      assertEquals(1, installedPluginIds.size)
      assertTrue(installedPluginIds.contains(ideaLight.idString))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.PLUGINS, true)
    }
  }

  fun `test do not install UI plugin when UI category is disabled`() {
    SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, false)
    try {
      pluginManager.pushChangesToIde(state {
        quickJump(enabled = false)
        typengo(enabled = true)
        ideaLight(enabled = true, category = SettingsCategory.UI)
      })

      val installedPluginIds = testPluginManager.installer.installedPluginIds
      // IdeaLight is a UI plugin, it doesn't fall under PLUGINS category
      assertEquals(1, installedPluginIds.size)
      assertTrue(installedPluginIds.contains(typengo.idString))
    }
    finally {
      SettingsSyncSettings.getInstance().setCategoryEnabled(SettingsCategory.UI, true)
    }
  }

  fun `test disable installed plugin`() {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump)
    pluginManager.updateStateFromIdeOnStart(null)

    assertPluginManagerState {
      quickJump(enabled = true)
    }

    pluginManager.pushChangesToIde(state {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    })

    assertFalse(quickJump.isEnabled)
    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      ideaLight(enabled = true, category = SettingsCategory.UI)
    }
  }

  fun `test disable two plugins at once`() {
    // install two plugins
    testPluginManager.addPluginDescriptors(pluginManager, quickJump, typengo)

    pluginManager.pushChangesToIde(state {
      quickJump(enabled = false)
      typengo(enabled = false)
    })

    assertFalse(quickJump.isEnabled)
    assertFalse(typengo.isEnabled)
  }

  fun `test update state from IDE`() {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump, typengo, git4idea)

    pluginManager.updateStateFromIdeOnStart(null)

    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = true)
    }

    testPluginManager.disablePlugin(git4idea.pluginId)

    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = true)
      git4idea(enabled = false)
    }

    testPluginManager.disablePlugin(typengo.pluginId)
    testPluginManager.enablePlugin(git4idea.pluginId)

    assertPluginManagerState {
      quickJump(enabled = true)
      typengo(enabled = false)
    }
  }

  fun `test do not remove entries about disabled plugins which are not installed`() {
    testPluginManager.addPluginDescriptors(pluginManager, typengo, git4idea)

    val savedState = state {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = true)
    }

    pluginManager.updateStateFromIdeOnStart(savedState)

    assertPluginManagerState {
      quickJump(enabled = false)
      typengo(enabled = true)
      // git4idea is removed because existing bundled enabled plugin is the default state
    }
  }

  fun `test push settings to IDE`() {
    testPluginManager.addPluginDescriptors(pluginManager, quickJump, typengo, git4idea)
    pluginManager.updateStateFromIdeOnStart(null)

    pluginManager.pushChangesToIde(state {
      quickJump(enabled = false)
      git4idea(enabled = false)
    })

    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = false)
    }

    pluginManager.pushChangesToIde(state {
      quickJump(enabled = false)
    })
    // no entry for the bundled git4idea plugin => it is enabled

    assertIdeState {
      quickJump(enabled = false)
      typengo(enabled = true)
      git4idea(enabled = true)
    }
  }

  fun `test when cross-ide sync is disabled then state of the ides is not applied ot updated`() {
    testPluginManager.addPluginDescriptors(pluginManager, typengo, git4idea)
    val savedState = statePerIde {
      ide(thisIde()) {
        typengo(enabled = true)
      }
      ide("otherIde") {
        quickJump(enabled = true)
        git4idea(enabled = false)
      }
    }
    pluginManager.pushChangesToIde(savedState)

    assertIdeState {
      typengo(enabled = true)
      git4idea(enabled = true)
    }
  }

  fun `test when cross-ide sync is enabled then the ide is updated by merged state`() {
    testPluginManager.addPluginDescriptors(pluginManager, typengo, git4idea)
    val savedState = statePerIde {
      ide(thisIde()) {
        typengo(enabled = true)
      }
      ide("otherIde") {
        quickJump(enabled = true)
        git4idea(enabled = false)
      }
    }

    SettingsSyncSettings.getInstance().syncPluginsAcrossIdes = true
    try {
      pluginManager.pushChangesToIde(savedState)

      assertIdeState {
        typengo(enabled = true)
        quickJump(enabled = true)
        git4idea(enabled = false)
      }
    }
    finally {
      SettingsSyncSettings.getInstance().state.reset()
    }
  }

  fun `test when cross-ide sync is enabled then in case of conflicts prefer data for this ide`() {
    testPluginManager.addPluginDescriptors(pluginManager, typengo, git4idea, quickJump)
    val savedState = statePerIde {
      ide(thisIde()) {
        typengo(enabled = true)
        quickJump(enabled = false)
      }
      ide("otherIde") {
        quickJump(enabled = true)
        git4idea(enabled = false)
      }
    }

    SettingsSyncSettings.getInstance().syncPluginsAcrossIdes = true
    try {
      pluginManager.pushChangesToIde(savedState)

      assertIdeState {
        typengo(enabled = true)
        quickJump(enabled = false)
        git4idea(enabled = false)
      }
    }
    finally {
      SettingsSyncSettings.getInstance().state.reset()
    }
  }

  fun `test when cross-ide sync is disabled then themes should still sync between ides`() {
    testPluginManager.addPluginDescriptors(pluginManager, ideaLight)
    val savedState = statePerIde {
      ide(thisIde()) {
      }
      ide("otherIde") {
        ideaLight(enabled = true)
      }
    }
    pluginManager.pushChangesToIde(savedState)

    assertIdeState {
      ideaLight(enabled = true)
    }
  }

  private fun assertPluginManagerState(build: StateBuilder.() -> Unit) {
    val expectedState = state(build)
    assertState(expectedState.plugins, pluginManager.getCurrentState().plugins)
  }

  private fun assertIdeState(build: StateBuilder.() -> Unit) {
    val expectedState = state(build)

    val actualState = PluginManagerProxy.getInstance().getPlugins().associate { plugin ->
      plugin.pluginId to PluginData(plugin.isEnabled)
    }
    assertState(expectedState.plugins, mapOf(thisIde() to actualState))
  }

  private fun assertState(expectedStates: Map<String, Map<PluginId, PluginData>>,
                          actualStates: Map<String, Map<PluginId, PluginData>>) {
    assertEquals(expectedStates.keys, actualStates.keys)
    val showIdeNameInAssertion = actualStates.keys.size > 1 || actualStates.keys.single() != thisIde()
    for (ide in actualStates.keys) {
      assertStateForIde(expectedStates[ide]!!, actualStates[ide]!!, if (showIdeNameInAssertion) ide else null)
    }
  }

  private fun assertStateForIde(expectedStates: Map<PluginId, PluginData>, actualStates: Map<PluginId, PluginData>,
                                ideName: String? = null) {
    fun stringifyStates(states: Map<PluginId, PluginData>) =
      states.entries
        .sortedBy { it.key }
        .joinToString { (id, data) -> "$id: ${enabledOrDisabled(data.enabled)}" }

    val forIde = ideName?.let { "for '$it' " } ?: ""
    if (expectedStates.size != actualStates.size) {
      assertEquals("Expected and actual states ${forIde}have different number of elements",
                   stringifyStates(expectedStates), stringifyStates(actualStates))
    }
    for ((expectedId, expectedData) in expectedStates) {
      val actualData = actualStates[expectedId]
      assertNotNull("Record for plugin $expectedId not found $forIde", actualData)
      assertEquals("Plugin $expectedId has incorrect state $forIde", expectedData.enabled, actualData!!.enabled)
    }
  }

  private fun enabledOrDisabled(value: Boolean?) = if (value == null) "null" else if (value) "enabled" else "disabled"

  private fun state(build: StateBuilder.() -> Unit): SettingsSyncPluginsState {
    val builder = StateBuilder()
    builder.build()
    return SettingsSyncPluginsState(builder.getPluginsState())
  }

  private fun statePerIde(build: PerIdeStateBuilder.() -> Unit) : SettingsSyncPluginsState {
    val builder = PerIdeStateBuilder()
    builder.build()
    return SettingsSyncPluginsState(builder.getPluginsState())
  }

  private class StateBuilder {
    val states = mutableMapOf<PluginId, PluginData>()

    operator fun TestPluginDescriptor.invoke(
      enabled: Boolean,
      category: SettingsCategory = SettingsCategory.PLUGINS): Pair<PluginId, PluginData>
    {
      val pluginData = PluginData(enabled, category)
      states[pluginId]= pluginData
      return this.pluginId to pluginData
    }

    fun getPluginsState(): Map<String, Map<PluginId, PluginData>> {
      return mapOf(thisIde() to states)
    }
  }

  private class PerIdeStateBuilder() {
    val statesForIdes = mutableMapOf<String, MutableMap<PluginId, PluginData>>()

    fun ide(ideName: String, build: StateBuilder.() -> Unit) {
      val builder = StateBuilder()
      builder.build()
      statesForIdes[ideName] = builder.states
    }

    fun getPluginsState(): Map<String, Map<PluginId, PluginData>> {
      return statesForIdes
    }
  }
}