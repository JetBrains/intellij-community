package com.intellij.settingsSync

import com.intellij.util.containers.map2Array
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MatrixIDEStartPluginManagerTest(
  private val ide: Boolean?,
  private val json: Boolean?
) : BasePluginManagerTest() {
  companion object {

    @JvmStatic
    @Parameterized.Parameters(name = "ide, plugins.json :{0}, {1}")
    fun parameters(): Array<Array<Any?>> {
      val tfn = arrayOf(true, false, null)
      val list = mutableListOf<Any>()
      for (ide in tfn) {
        for (json in tfn) {
          list.add(arrayOf(ide, json))
        }
      }
      return list.map2Array { it as Array<Any?> }
    }
  }

  @Test
  fun `check non-bundled plugin`() {
    val myPlugin = TestPluginDescriptor(
      "myPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = false
    )
    if (ide != null) {
      testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    }
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })

    assertIdeState(state {
      if (ide != null) {
        myPlugin(enabled = ide)
      }
    })
    assertPluginManagerState(state {
      if (ide != null) {
        myPlugin(enabled = ide)
      }
      else if (json != null) { // ide == null
        myPlugin(enabled = false)
      }
    })
  }

  @Test
  fun `check bundled plugin`() {
    if (ide == null) {
      return // do not test absent bundled plugin
    }
    val myPlugin = TestPluginDescriptor(
      "myBundledPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = true
    )
    testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
    assertIdeState(state {
      myPlugin(enabled = ide)
    })
    assertPluginManagerState(state {
      if (!ide) {
        myPlugin(enabled = false)
      }
    })
  }

  @Test
  fun `check essential plugin`() {
    if (ide == null || !ide) {
      return // do not test absent bundled plugin
    }
    val myPlugin = TestPluginDescriptor(
      "myBundledPlugin",
      listOf(TestPluginDependency("com.intellij.modules.platform", isOptional = false)),
      bundled = true, essential = true
    )
    testPluginManager.addPluginDescriptors(myPlugin.withEnabled(ide))
    pluginManager.updateStateFromIdeOnStart(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
    assertIdeState(state {
      myPlugin(enabled = true)
    })
    assertPluginManagerState(state {
      if (json != null) {
        myPlugin(enabled = json)
      }
    })
  }
}