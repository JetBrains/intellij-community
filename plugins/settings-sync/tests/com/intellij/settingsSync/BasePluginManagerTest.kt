package com.intellij.settingsSync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.settingsSync.plugins.PluginManagerProxy
import com.intellij.settingsSync.plugins.SettingsSyncPluginManager
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState
import com.intellij.settingsSync.plugins.SettingsSyncPluginsState.PluginData
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService

abstract class BasePluginManagerTest : LightPlatformTestCase() {
  internal lateinit var pluginManager: SettingsSyncPluginManager
  internal lateinit var testPluginManager: TestPluginManager

  internal val quickJump = TestPluginDescriptor(
    "QuickJump",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )
  internal val typengo = TestPluginDescriptor(
    "codeflections.typengo",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false))
  )
  internal val ideaLight = TestPluginDescriptor(
    "color.scheme.IdeaLight",
    listOf(TestPluginDependency("com.intellij.modules.lang", isOptional = false))
  )
  internal val git4idea = TestPluginDescriptor(
    "git4idea",
    listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
    bundled = true
  )
  internal val javascript = TestPluginDescriptor(
    "JavaScript",
    listOf(TestPluginDependency("css", false)),
    bundled = true,
    essential = true
  )
  internal val css = TestPluginDescriptor(
    "css",
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

  internal fun assertPluginManagerState(build: StateBuilder.() -> Unit) {
    assertPluginManagerState(state(build))
  }

  internal fun assertPluginManagerState(expectedState: SettingsSyncPluginsState) {
    val state = pluginManager.state
    assertPluginsState(expectedState.plugins, state.plugins)
  }

  internal fun assertIdeState(build: StateBuilder.() -> Unit) {
    assertIdeState(state(build))
  }

  internal fun assertIdeState(expectedState: SettingsSyncPluginsState) {

    val actualState = PluginManagerProxy.getInstance().getPlugins().associate { plugin ->
      plugin.pluginId to PluginData(plugin.isEnabled)
    }
    assertPluginsState(expectedState.plugins, actualState)
  }
}

internal fun state(build: StateBuilder.() -> Unit): SettingsSyncPluginsState {
  val builder = StateBuilder()
  builder.build()
  return SettingsSyncPluginsState(builder.states)
}

internal class StateBuilder {
  val states = mutableMapOf<PluginId, PluginData>()

  operator fun TestPluginDescriptor.invoke(
    enabled: Boolean,
    category: SettingsCategory = SettingsCategory.PLUGINS): Pair<PluginId, PluginData> {

    val pluginData = PluginData(enabled, category)
    states[pluginId] = pluginData
    return this.pluginId to pluginData
  }
}

internal fun assertPluginsState(expectedStates: Map<PluginId, PluginData>, actualStates: Map<PluginId, PluginData>) {
  fun stringifyStates(states: Map<PluginId, PluginData>) =
    states.entries
      .sortedBy { it.key }
      .joinToString { (id, data) -> "$id: ${enabledOrDisabled(data.enabled)}" }

  if (expectedStates.size != actualStates.size) {
    LightPlatformTestCase.assertEquals("Expected and actual states have different number of elements",
                                       stringifyStates(expectedStates), stringifyStates(actualStates))
  }
  for ((expectedId, expectedData) in expectedStates) {
    val actualData = actualStates[expectedId]
    LightPlatformTestCase.assertNotNull("Record for plugin $expectedId not found", actualData)
    LightPlatformTestCase.assertEquals("Plugin $expectedId has incorrect state", expectedData.enabled, actualData!!.enabled)
  }
}

private fun enabledOrDisabled(value: Boolean?) = if (value == null) "null" else if (value) "enabled" else "disabled"
