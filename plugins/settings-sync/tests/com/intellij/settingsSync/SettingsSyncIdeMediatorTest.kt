package com.intellij.settingsSync

import com.intellij.configurationStore.ChildlessComponentStore
import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.RoamingType
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.pathString

@RunWith(JUnit4::class)
class SettingsSyncIdeMediatorTest {

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
}