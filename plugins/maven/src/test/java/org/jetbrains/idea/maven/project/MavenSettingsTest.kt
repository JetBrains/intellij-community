// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.configurationStore.deserializeState
import com.intellij.configurationStore.jdomSerializer
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.Companion.getInstance
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.replaceService
import org.jdom.output.XMLOutputter
import com.intellij.maven.testFramework.fixtures.mavenFixture
import org.jetbrains.idea.maven.utils.MavenSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@RunInEdt
class MavenSettingsTest {
  private val maven by mavenFixture()
  private val disposable by disposableFixture()

  @Test
  fun testCloningGeneralSettingsWithoutListeners() {
    val log: Array<String> = arrayOf<String>("")

    val s = MavenGeneralSettings()
    s.addListener(object : MavenGeneralSettings.Listener {
      override fun changed() {
        log[0] += "changed "
      }
    })

    s.setMavenHomeType(MavenWrapper)
    assertEquals("changed ", log[0])

    s.clone().setMavenHomeType(BundledMaven3)
    assertEquals("changed ", log[0])
  }

  @Test
  fun testImportingSettings() {
    val javaHome = System.getenv("JAVA_HOME")
    if (javaHome != null) {
      val javaHomePath = Path.of(javaHome)
      if (Files.exists(javaHomePath)) {
        VfsRootAccess.allowRootAccess(disposable, javaHomePath.toAbsolutePath().toString())
      }
    }

    assertEquals(MavenImportingSettings(), MavenImportingSettings())
    val importingConfigurable = MavenImportingConfigurable(maven.project)
    importingConfigurable.reset()
    assertFalse(importingConfigurable.isModified())
  }

  @Test
  fun testNotModifiedAfterCreation() {
    val s = MavenSettings(maven.project)
    s.createComponent()
    s.reset()
    try {
      assertFalse(s.isModified())
    }
    finally {
      s.disposeUIResources() //prevent memory leaks
    }

    for (each in s.getConfigurables()) {
      each.createComponent()
      each.reset()
      try {
        assertFalse(each.isModified())
      }
      finally {
        each.disposeUIResources() //prevent memory leaks
      }
    }
  }

  @Suppress("deprecation")
  @Test
  fun testMavenSettingsMigration() {
    replaceService<ExternalSystemProjectTrackerSettings>(maven.project, ExternalSystemProjectTrackerSettings::class.java,
                                                         AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(maven.project)
      val workspaceSettingsComponent = loadWorkspaceComponent(
        """
          <MavenImportPreferences>
            <option name="importingSettings">
              <MavenImportingSettings>
                <option name="importAutomatically" value="true" />
              </MavenImportingSettings>
            </option>
          </MavenImportPreferences>

          """.trimIndent())
      assertFalse(workspaceSettingsComponent.settings.importingSettings.isImportAutomatically())
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.ALL, projectTrackerSettings.autoReloadType)
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
    replaceService<ExternalSystemProjectTrackerSettings>(maven.project, ExternalSystemProjectTrackerSettings::class.java,
                                                                    AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(maven.project)
      val workspaceSettingsComponent = loadWorkspaceComponent(
        """
          <MavenImportPreferences>
            <option name="importingSettings">
              <MavenImportingSettings>
                <option name="importAutomatically" value="false" />
              </MavenImportingSettings>
            </option>
          </MavenImportPreferences>

          """.trimIndent())
      assertFalse(workspaceSettingsComponent.settings.importingSettings.isImportAutomatically())
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE, projectTrackerSettings.autoReloadType)
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
    replaceService<ExternalSystemProjectTrackerSettings>(maven.project, ExternalSystemProjectTrackerSettings::class.java,
                                                                    AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(maven.project)
      val workspaceSettingsComponent = loadWorkspaceComponent("<MavenWorkspacePersistedSettings />")
      assertFalse(workspaceSettingsComponent.settings.importingSettings.isImportAutomatically())
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE, projectTrackerSettings.autoReloadType)
      assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
  }

  private fun loadWorkspaceComponent(rawWorkspaceSettingsComponent: CharSequence): MavenWorkspaceSettingsComponent {
    try {
      val workspaceSettingsComponent = MavenWorkspaceSettingsComponent(maven.project)
      val workspaceSettingsElement = JDOMUtil.load(rawWorkspaceSettingsComponent)
      val workspaceSettings = deserializeState<MavenWorkspacePersistedSettings>(workspaceSettingsElement,
                                                                                MavenWorkspacePersistedSettings::class.java)
      workspaceSettingsComponent.loadState(workspaceSettings!!)
      return workspaceSettingsComponent
    }
    catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  companion object {
    private fun <T : Any> replaceService(componentManager: ComponentManager, serviceInterface: Class<T>, instance: T, action: Runnable) {
      val parentDisposable = Disposer.newDisposable()
      try {
        componentManager.replaceService<T>(serviceInterface, instance, parentDisposable)
        action.run()
      }
      finally {
        Disposer.dispose(parentDisposable)
      }
    }

    private fun storeWorkspaceComponent(workspaceSettingsComponent: MavenWorkspaceSettingsComponent): String? {
      try {
        val workspaceSettings = workspaceSettingsComponent.getState()
        val jdomSerializer = jdomSerializer
        val serializationFilter = jdomSerializer.getDefaultSerializationFilter()
        val workspaceSettingsElement = jdomSerializer.serialize<MavenWorkspacePersistedSettings>(workspaceSettings, serializationFilter, true)
        return XMLOutputter().outputString(workspaceSettingsElement)
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }
}
