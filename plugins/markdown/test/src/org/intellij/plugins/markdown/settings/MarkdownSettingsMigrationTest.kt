// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@TestApplication
class MarkdownSettingsMigrationTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val settingsSnapshot = testFixture {
    val originalState = MarkdownSettings.getInstance().state
    initialized(Unit) {
      MarkdownSettings.getInstance().loadState(originalState)
    }
  }
  private val projectWithSettings = testFixture {
    settingsSnapshot.init()
    val path = tempPathFixture().init()
    withContext(Dispatchers.IO) {
      path.resolve(".idea").createDirectories().resolve("markdown.xml").writeText(
        """
        <project version="4">
          <component name="MarkdownSettings">
            <option name="areInjectionsEnabled" value="false" />
          </component>
        </project>
        """.trimIndent()
      )
    }
    initialized(path) {}
  }
  private val project by projectFixture(projectWithSettings, createTestOpenProjectOptions(runPostStartUpActivities = false))

  @BeforeEach
  fun resetSettings() {
    MarkdownSettings.getInstance().loadState(MarkdownSettingsState())
  }

  @Test
  fun `startup reconciles settings from a trusted project`(): Unit = timeoutRunBlocking {
    MarkdownSettingsMigration().execute(project)

    assertFalse(MarkdownSettings.getInstance().areInjectionsEnabled)
    assertTrue(PropertiesComponent.getInstance(project).getBoolean("markdown.settings.migrated.v1"))
  }

  @Test
  fun `startup skips the default project`(): Unit = timeoutRunBlocking {
    MarkdownSettingsMigration().execute(ProjectManager.getInstance().defaultProject)

    assertTrue(MarkdownSettings.getInstance().state.areInjectionsEnabled)
  }

  @Test
  @SystemProperty(propertyKey = TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, propertyValue = "false")
  fun `an untrusted project can migrate after it becomes trusted`(): Unit = timeoutRunBlocking {
    TrustedProjects.setProjectTrusted(project, false)
    val migration = MarkdownSettingsMigration()

    migration.execute(project)

    assertTrue(MarkdownSettings.getInstance().areInjectionsEnabled)
    assertFalse(PropertiesComponent.getInstance(project).getBoolean("markdown.settings.migrated.v1"))

    val changed = CompletableDeferred<Unit>()
    application.messageBus.connect(disposable).subscribe(MarkdownSettings.ChangeListener.TOPIC, object : MarkdownSettings.ChangeListener {
      override fun settingsChanged(settings: MarkdownSettings) {
        changed.complete(Unit)
      }
    })
    TrustedProjects.setProjectTrusted(project, true)
    migration.onProjectTrusted(project)
    changed.await()

    withContext(Dispatchers.EDT) {
      assertFalse(MarkdownSettings.getInstance().areInjectionsEnabled)
      assertTrue(PropertiesComponent.getInstance(project).getBoolean("markdown.settings.migrated.v1"))
    }
  }
}
