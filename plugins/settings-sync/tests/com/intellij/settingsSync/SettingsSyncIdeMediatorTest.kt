package com.intellij.settingsSync

import com.intellij.configurationStore.ChildlessComponentStore
import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Assert
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
}