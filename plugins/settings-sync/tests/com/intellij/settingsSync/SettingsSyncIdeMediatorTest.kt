package com.intellij.settingsSync

import com.intellij.configurationStore.ChildlessComponentStore
import com.intellij.configurationStore.StateStorageManager
import com.intellij.configurationStore.getStateSpec
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.pathString

@RunWith(JUnit4::class)
class SettingsSyncIdeMediatorTest : BasePlatformTestCase() {

  @JvmField @Rule
  val memoryFs = InMemoryFsRule()

  @Test
  fun `process children with subfolders`() {
    val rootConfig = memoryFs.fs.getPath("/appconfig")
    val componentStore = object : ChildlessComponentStore() {
      override val storageManager: StateStorageManager
        get() = TODO("Not yet implemented")

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val mediator = SettingsSyncIdeMediatorImpl(componentStore, rootConfig, {true})

    val fileTypes = rootConfig / "filetypes"
    val code = (fileTypes / "code").createDirectories()
    (code / "mytemplate.kt").createFile()

    val visited = mutableSetOf<String>()
    mediator.processChildren(fileTypes.pathString, RoamingType.DEFAULT, {true}, processor = { name, _, _ ->
      visited += name
true
    })

    assertEquals(setOf("mytemplate.kt"), visited)
  }

  @Test
  fun `respect SettingSyncState`() {
    val rootConfig = memoryFs.fs.getPath("/appconfig")
    val componentStore = object : ChildlessComponentStore() {
      override val storageManager: StateStorageManager
        get() = ApplicationManager.getApplication().stateStore.storageManager

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val mediator = SettingsSyncIdeMediatorImpl(componentStore, rootConfig, { true })
    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), null)
    val settingsSyncXmlState = FileState.Modified("options/settingsSync.xml", """
<application>
  <component name="SettingsSyncSettings">
    <option name="disabledSubcategories">
      <map>
        <entry key="PLUGINS">
          <value>
            <list>
              <option value="org.vlang" />
            </list>
          </value>
        </entry>
      </map>
    </option>
  </component>
</application>      
    """.trimIndent().toByteArray())
    val snapshot = SettingsSnapshot(metaInfo, setOf(settingsSyncXmlState), null, emptyMap(), emptySet())
    val syncState = SettingsSyncStateHolder(SettingsSyncSettings.State())
    syncState.syncEnabled = true
    syncState.setCategoryEnabled(SettingsCategory.CODE, false)
    syncState.setSubcategoryEnabled(SettingsCategory.PLUGINS, "IdeaVIM", false)
    mediator.applyToIde(snapshot, syncState)
    Assert.assertTrue(SettingsSyncSettings.getInstance().syncEnabled)
    Assert.assertFalse(SettingsSyncSettings.getInstance().migrationFromOldStorageChecked)
    Assert.assertFalse(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.CODE))
    Assert.assertTrue(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.UI))
    Assert.assertTrue(SettingsSyncSettings.getInstance().isCategoryEnabled(SettingsCategory.SYSTEM))

    Assert.assertTrue(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, "org.vlang"))
    Assert.assertFalse(SettingsSyncSettings.getInstance().isSubcategoryEnabled(SettingsCategory.PLUGINS, "IdeaVIM"))
  }

  @TestFor(issues = ["IDEA-324914"])
  @Test
  fun `process files2apply last`() {
    val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
    val rootConfig = memoryFs.fs.getPath("/IDEA-324914/appConfig")
    val componentStore = object : ChildlessComponentStore() {
      override val storageManager: StateStorageManager
        get() = ApplicationManager.getApplication().stateStore.storageManager

      override fun setPath(path: Path) {
        TODO("Not yet implemented")
      }
    }
    val callbackCalls = mutableListOf<String>()
    val firstComponent = FirstComponent({ callbackCalls.add("First") })
    componentManager.registerComponentInstance(FirstComponent::class.java, firstComponent)
    componentStore.initComponent(firstComponent, null, null)
    componentStore.storageManager.getStateStorage(getStateSpec(FirstComponent::class.java)!!.storages[0]).createSaveSessionProducer()

    val secondComponent = SecondComponent({ callbackCalls.add("Second") })
    componentManager.registerComponentInstance(SecondComponent::class.java, secondComponent)
    componentStore.initComponent(secondComponent, null, null)
    componentStore.storageManager.getStateStorage(getStateSpec(SecondComponent::class.java)!!.storages[0]).createSaveSessionProducer()

    val mediator = SettingsSyncIdeMediatorImpl(componentStore, rootConfig, { true })
    val metaInfo = SettingsSnapshot.MetaInfo(Instant.now(), null)
    val snapshot = SettingsSnapshot(metaInfo, setOf(FileState.Modified("options/first.xml", """
      <application>
  <component name="FirstComponent">
    <option name="strg" value="aaa" />
  </component>
  </application>
    """.trimIndent().toByteArray()), FileState.Modified("options/second.xml", """
      <application>
  <component name="SecondComponent">
    <option name="intt" value="1" />
  </component>
  </application>
    """.trimIndent().toByteArray())), null, emptyMap(), emptySet())
    val syncState = SettingsSyncStateHolder(SettingsSyncSettings.State())
    syncState.syncEnabled = true
    try {
      mediator.activateStreamProvider()
      mediator.applyToIde(snapshot, syncState)
      Assert.assertEquals(2, callbackCalls.size)
      Assert.assertEquals("First", callbackCalls[0])
      Assert.assertEquals("Second", callbackCalls[1])
      callbackCalls.clear()
      mediator.files2applyLast.add("first.xml")
      val newSnapshot = SettingsSnapshot(metaInfo, setOf(FileState.Modified("options/first.xml", """
      <application>
  <component name="FirstComponent">
    <option name="strg" value="bbb" />
  </component>
  </application>
    """.trimIndent().toByteArray()), FileState.Modified("options/second.xml", """
      <application>
  <component name="SecondComponent">
    <option name="intt" value="2" />
  </component>
  </application>
    """.trimIndent().toByteArray())), null, emptyMap(), emptySet())
      mediator.applyToIde(newSnapshot, syncState)
      Assert.assertEquals(2, callbackCalls.size)
      Assert.assertEquals("Second", callbackCalls[0])
      Assert.assertEquals("First", callbackCalls[1])
    } finally {
      mediator.files2applyLast.remove("first.xml")
      mediator.removeStreamProvider()
    }
  }


  @State(
    name = "FirstComponent",
    storages = [Storage("first.xml")],
    category = SettingsCategory.SYSTEM
  )
  internal class FirstComponent(private val loadStateCallback: ()->Unit) : PersistentStateComponent<FirstComponent.FirstState>{

    var internalState = FirstState()
    data class FirstState(
      @JvmField
      var strg: String = ""
    )

    override fun getState(): FirstState{
      return internalState
    }

    override fun loadState(state: FirstState) {
      this.internalState = state
      if (loadStateCallback != null){
        loadStateCallback()
      }
    }
  }

  @State(
    name = "SecondComponent",
    storages = [Storage("second.xml")],
    category = SettingsCategory.SYSTEM
  )
  internal class SecondComponent(private val loadStateCallback: ()->Unit) : PersistentStateComponent<SecondComponent.SecondState>{

    var internalState = SecondState()
    data class SecondState(
      @JvmField
      var intt: Int = 0
    )

    override fun getState(): SecondState{
      return internalState
    }

    override fun loadState(state: SecondState) {
      this.internalState = state
      if (loadStateCallback != null){
        loadStateCallback()
      }
    }
  }
}