// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.configurationStore.deserializeState
import com.intellij.configurationStore.jdomSerializer
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings.Companion.getInstance
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.replaceService
import junit.framework.TestCase
import org.jdom.output.XMLOutputter
import org.jetbrains.idea.maven.utils.MavenSettings

class MavenSettingsTest : MavenTestCase() {
  fun testCloningGeneralSettingsWithoutListeners() {
    val log: Array<String> = arrayOf<String>("")

    val s = MavenGeneralSettings()
    s.addListener(object : MavenGeneralSettings.Listener {
      override fun changed() {
        log[0] += "changed "
      }
    })

    s.setMavenHomeType(MavenWrapper)
    TestCase.assertEquals("changed ", log[0])

    s.clone().setMavenHomeType(BundledMaven3)
    TestCase.assertEquals("changed ", log[0])
  }

  fun testImportingSettings() {
    val javaHome = System.getenv("JAVA_HOME")
    if (javaHome != null) {
      allowAccessToDirsIfExists(System.getenv("JAVA_HOME"))
    }

    assertEquals(MavenImportingSettings(), MavenImportingSettings())
    val importingConfigurable = MavenImportingConfigurable(project)
    importingConfigurable.reset()
    assertFalse(importingConfigurable.isModified())
  }

  fun testNotModifiedAfterCreation() {
    val s = MavenSettings(project)
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
  fun testMavenSettingsMigration() {
    Companion.replaceService<ExternalSystemProjectTrackerSettings>(project, ExternalSystemProjectTrackerSettings::class.java,
                                                                   AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(project)
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
      TestCase.assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
    replaceService<ExternalSystemProjectTrackerSettings>(project, ExternalSystemProjectTrackerSettings::class.java,
                                                                    AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(project)
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
      TestCase.assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
    replaceService<ExternalSystemProjectTrackerSettings>(project, ExternalSystemProjectTrackerSettings::class.java,
                                                                    AutoImportProjectTrackerSettings(), Runnable {
      val projectTrackerSettings = getInstance(project)
      val workspaceSettingsComponent = loadWorkspaceComponent("<MavenWorkspacePersistedSettings />")
      assertFalse(workspaceSettingsComponent.settings.importingSettings.isImportAutomatically())
      assertEquals(ExternalSystemProjectTrackerSettings.AutoReloadType.SELECTIVE, projectTrackerSettings.autoReloadType)
      TestCase.assertEquals("<MavenWorkspacePersistedSettings />", storeWorkspaceComponent(workspaceSettingsComponent))
    })
  }

  private fun loadWorkspaceComponent(rawWorkspaceSettingsComponent: CharSequence): MavenWorkspaceSettingsComponent {
    try {
      val workspaceSettingsComponent = MavenWorkspaceSettingsComponent(project)
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
