// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.pandoc.PandocSettings
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

@TestApplication
@Suppress("DEPRECATION")
class MarkdownSettingsReconciliationTest {
  private val settingsSnapshot = testFixture {
    val originalMarkdownState = MarkdownSettings.getInstance().state
    initialized(Unit) {
      MarkdownSettings.getInstance().loadState(originalMarkdownState)
    }
  }
  private val projectDirectory = tempPathFixture()
  private val projectWithSettings = testFixture {
    settingsSnapshot.init()
    val path = projectDirectory.init()
    withContext(Dispatchers.IO) {
      val directory = path.resolve(".idea").createDirectories()
      directory.resolve("markdown.xml").writeText(
        $$"""
        <project version="4">
          <component name="MarkdownSettings">
            <option name="areInjectionsEnabled" value="false" />
            <option name="verticalSplit" value="false" />
            <option name="useCustomStylesheetPath" value="true" />
            <option name="customStylesheetPath" value="$PROJECT_DIR$/preview.css" />
            <option name="useCustomStylesheetText" value="true" />
            <option name="customStylesheetText" value="body { color: red; }" />
            <option name="fontSize" value="10" />
          </component>
        </project>
        """.trimIndent()
      )
      directory.resolve("pandoc.xml").writeText(
        $$"""
        <project version="4">
          <component name="Pandoc.Settings">
            <option name="pathToPandoc" value="$PROJECT_DIR$/pandoc" />
            <option name="pathToImages" value="$PROJECT_DIR$/images" />
          </component>
        </project>
        """.trimIndent()
      )
    }
    initialized(path) {}
  }
  private val project by projectFixture(projectWithSettings, createTestOpenProjectOptions(runPostStartUpActivities = false))
  private val newProject by projectFixture(openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false))
  private val otherProject by projectWithState(MarkdownSettingsState().apply {
    areInjectionsEnabled = true
    showProblemsInCodeBlocks = true
    enabledExtensions["test"] = false
  })
  private val projectWithExplicitValues by projectWithState(MarkdownSettingsState().apply {
    areInjectionsEnabled = false
    customStylesheetText = "body { color: red; }"
    useCustomStylesheetText = true
    useFileDirectoryForCommands = true
    showProblemsInCodeBlocks = true
  })

  private fun projectWithState(state: MarkdownSettingsState) = projectFixture(
    testFixture {
      settingsSnapshot.init()
      val path = tempPathFixture().init()
      withContext(Dispatchers.IO) {
        val component = XmlSerializer.serialize(state, SkipDefaultsSerializationFilter())
        component.name = "component"
        component.setAttribute("name", "MarkdownSettings")
        val xml = Element("project").setAttribute("version", "4").addContent(component)
        path.resolve(".idea").createDirectories().resolve("markdown.xml").writeText(JDOMUtil.write(xml))
      }
      initialized(path) {}
    },
    createTestOpenProjectOptions(runPostStartUpActivities = false)
  )

  @BeforeEach
  fun resetSettings() {
    MarkdownSettings.getInstance().loadState(MarkdownSettingsState())
  }

  @Test
  fun `saved project preferences migrate without paths and preserve the application font`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.fontSize = 22

    reconcile(project)

    assertFalse(settings.areInjectionsEnabled)
    assertFalse(settings.isVerticalSplit)
    assertFalse(settings.state.useCustomStylesheetPath)
    assertNull(settings.state.customStylesheetPath)
    assertTrue(settings.useCustomStylesheetText)
    assertEquals("body { color: red; }", settings.customStylesheetText)
    assertEquals(22, settings.fontSize)
  }

  @Test
  fun `later projects replace defaults and preserve non-default settings`(): Unit = timeoutRunBlocking {
    reconcile(otherProject)
    val settings = MarkdownSettings.getInstance()
    assertNull(settings.customStylesheetText)

    reconcile(project)

    assertFalse(settings.areInjectionsEnabled)
    assertTrue(settings.showProblemsInCodeBlocks)
    assertFalse(settings.isVerticalSplit)
    assertTrue(settings.useCustomStylesheetText)
    assertEquals("body { color: red; }", settings.customStylesheetText)
    withContext(Dispatchers.EDT) {
      val previousCount = settings.stateModificationCount
      settings.reconcileWithProject(project)
      assertEquals(previousCount, settings.stateModificationCount)
    }
  }

  @Test
  fun `a reconciled project does not restore cleared application settings`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    reconcile(project)
    settings.loadState(MarkdownSettingsState())

    reconcile(project)

    assertTrue(settings.state.areInjectionsEnabled)
    assertNull(settings.customStylesheetText)

    reconcile(otherProject)

    assertTrue(settings.areInjectionsEnabled)
    assertTrue(settings.showProblemsInCodeBlocks)
  }

  @Test
  fun `a reconciliation with no changes still marks the project`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.areInjectionsEnabled = false
    settings.isVerticalSplit = false
    settings.useCustomStylesheetText = true
    settings.customStylesheetText = "body { color: blue; }"
    val previousCount = settings.stateModificationCount

    reconcile(project)

    assertEquals(previousCount, settings.stateModificationCount)
    settings.loadState(MarkdownSettingsState())
    reconcile(project)
    assertTrue(settings.state.areInjectionsEnabled)
    assertNull(settings.customStylesheetText)
  }

  @Test
  fun `each reconciliation advances the modification count`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    withContext(Dispatchers.EDT) {
      settings.reconcileWithProject(project)
      val previousCount = settings.stateModificationCount

      settings.reconcileWithProject(otherProject)

      assertTrue(settings.stateModificationCount > previousCount)
      assertFalse(settings.areInjectionsEnabled)
      assertFalse(settings.isVerticalSplit)
      assertTrue(settings.showProblemsInCodeBlocks)
      val updatedCount = settings.stateModificationCount
      settings.reconcileWithProject(otherProject)
      assertEquals(updatedCount, settings.stateModificationCount)
    }
  }

  @Test
  fun `extension reconciliation advances the modification count`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    val previousCount = settings.stateModificationCount
    withContext(Dispatchers.EDT) {
      settings.reconcileWithProject(otherProject)
    }

    assertTrue(settings.stateModificationCount > previousCount)
    assertEquals(false, settings.state.enabledExtensions["test"])
  }

  @Test
  fun `explicit defaults and cleared values allow reconciliation after saving`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.areInjectionsEnabled = true
    settings.customStylesheetText = null
    settings.useCustomStylesheetText = false
    settings.useFileDirectoryForCommands = null
    settings.loadState(XmlSerializer.deserialize(
      XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter()), MarkdownSettingsState::class.java
    ))
    reconcile(projectWithExplicitValues)

    assertFalse(settings.areInjectionsEnabled)
    assertEquals("body { color: red; }", settings.customStylesheetText)
    assertTrue(settings.useCustomStylesheetText)
    assertEquals(true, settings.useFileDirectoryForCommands)
    assertTrue(settings.showProblemsInCodeBlocks)
  }

  @Test
  fun `stylesheet text and its enabled state come from the same project`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    settings.customStylesheetText = "body { color: blue; }"

    reconcile(project)

    assertEquals("body { color: blue; }", settings.customStylesheetText)
    assertFalse(settings.useCustomStylesheetText)
  }

  @Test
  fun `application migration preserves existing project Pandoc values`(): Unit = timeoutRunBlocking {
    val savedSettings = PandocSettings.getInstance(project)
    val newSettings = PandocSettings.getInstance(newProject)
    assertEquals(projectWithSettings.get().resolve("pandoc").toString(), project.service<PandocSettings>().state.pathToPandoc)
    assertEquals(projectWithSettings.get().resolve("images").toString(), savedSettings.pathToImages)
    assertNull(newProject.service<PandocSettings>().state.pathToPandoc)
    assertNull(newSettings.pathToImages)

    newProject.service<PandocSettings>().loadState(PandocSettings.State().apply { pathToPandoc = "/another/pandoc" })
    newSettings.pathToImages = "/another/images"
    reconcile(project)

    assertEquals(projectWithSettings.get().resolve("pandoc").toString(), project.service<PandocSettings>().state.pathToPandoc)
    assertEquals("/another/pandoc", newProject.service<PandocSettings>().state.pathToPandoc)
    assertEquals(projectWithSettings.get().resolve("images").toString(), savedSettings.pathToImages)
    assertEquals("/another/images", newSettings.pathToImages)
  }

  @Test
  fun `saving the images destination preserves the legacy executable path`(): Unit = timeoutRunBlocking {
    val settings = PandocSettings.getInstance(project)
    settings.pathToImages = projectWithSettings.get().resolve("other-images").toString()
    assertTrue(saveSettings(project, forceSavingAllSettings = true))

    val saved = withContext(Dispatchers.IO) { projectWithSettings.get().resolve(".idea/pandoc.xml").readText() }
    assertTrue(saved.contains($$"name=\"pathToPandoc\" value=\"$PROJECT_DIR$/pandoc\""), saved)
    assertTrue(saved.contains($$"name=\"pathToImages\" value=\"$PROJECT_DIR$/other-images\""), saved)
  }

  @Test
  fun `clearing the images destination preserves the legacy executable path`(): Unit = timeoutRunBlocking {
    val settings = PandocSettings.getInstance(project)
    assertEquals(projectWithSettings.get().resolve("images").toString(), settings.pathToImages)
    settings.pathToImages = null
    assertTrue(saveSettings(project, forceSavingAllSettings = true))

    val saved = withContext(Dispatchers.IO) { projectWithSettings.get().resolve(".idea/pandoc.xml").readText() }
    assertTrue(saved.contains("<component name=\"Pandoc.Settings\">"), saved)
    assertTrue(saved.contains($$"name=\"pathToPandoc\" value=\"$PROJECT_DIR$/pandoc\""), saved)
    assertFalse(saved.contains("pathToImages"), saved)
    settings.loadState(XmlSerializer.deserialize(
      XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter()), PandocSettings.State::class.java
    ))
    assertNull(settings.pathToImages)
  }

  @Test
  fun `later project values do not replace migrated settings`(): Unit = timeoutRunBlocking {
    reconcile(project)
    val settings = MarkdownSettings.getInstance()

    withContext(Dispatchers.EDT) {
      settings.reconcileWithProject(newProject)
    }

    assertFalse(settings.areInjectionsEnabled)
    assertFalse(settings.isVerticalSplit)
  }

  @Test
  fun `existing stylesheet choices remain separate for each project`(): Unit = timeoutRunBlocking {
    val stylesheet = MarkdownStylesheetSettings.getInstance(project)
    val otherStylesheet = MarkdownStylesheetSettings.getInstance(newProject)
    assertEquals(projectWithSettings.get().resolve("preview.css").toString(), stylesheet.customStylesheetPath)
    assertTrue(stylesheet.useCustomStylesheetPath)
    assertNull(otherStylesheet.customStylesheetPath)
    assertFalse(otherStylesheet.useCustomStylesheetPath)

    otherStylesheet.customStylesheetPath = "/another/preview.css"
    otherStylesheet.useCustomStylesheetPath = true

    reconcile(project)

    assertEquals(projectWithSettings.get().resolve("preview.css").toString(), stylesheet.customStylesheetPath)
    assertEquals("/another/preview.css", otherStylesheet.customStylesheetPath)
    assertNull(MarkdownSettings.getInstance().state.customStylesheetPath)
    assertFalse(MarkdownSettings.getInstance().state.useCustomStylesheetPath)
  }

  @Test
  fun `clearing the project stylesheet preserves legacy settings and records the reset`(): Unit = timeoutRunBlocking {
    val path = projectWithSettings.get().resolve(".idea/markdown.xml")
    val legacyComponent = Regex("<component name=\"MarkdownSettings\">.*?</component>", RegexOption.DOT_MATCHES_ALL)
    val original = withContext(Dispatchers.IO) { legacyComponent.find(path.readText())?.value }
    val stylesheet = MarkdownStylesheetSettings.getInstance(project)
    assertTrue(stylesheet.useCustomStylesheetPath)

    stylesheet.useCustomStylesheetPath = false
    stylesheet.customStylesheetPath = null
    assertFalse(stylesheet.useCustomStylesheetPath)
    assertNull(stylesheet.customStylesheetPath)
    assertTrue(saveSettings(project, forceSavingAllSettings = true))

    val saved = withContext(Dispatchers.IO) { path.readText() }
    assertEquals(requireNotNull(original), legacyComponent.find(saved)?.value)
    val stylesheetState = Regex("<component name=\"MarkdownStylesheetSettings\">.*?</component>", RegexOption.DOT_MATCHES_ALL)
      .find(saved)?.value
    assertEquals(
      """
      <component name="MarkdownStylesheetSettings">
          <option name="customStylesheetPath" value="" />
          <option name="useCustomStylesheetPath" value="false" />
        </component>
      """.trimIndent(),
      stylesheetState
    )
  }

  @Test
  fun `migration leaves legacy settings files unchanged`(): Unit = timeoutRunBlocking {
    val directory = projectWithSettings.get().resolve(".idea")
    val files = listOf(directory.resolve("markdown.xml"), directory.resolve("pandoc.xml"))
    val originalContents = withContext(Dispatchers.IO) { files.map { it.readText() } }

    reconcile(project)
    assertTrue(saveSettings(project, forceSavingAllSettings = true))

    val savedContents = withContext(Dispatchers.IO) { files.map { it.readText() } }
    assertEquals(originalContents, savedContents)
    val workspace = withContext(Dispatchers.IO) { directory.resolve("workspace.xml").readText() }
    assertTrue(workspace.contains("markdown.settings.migrated.v1"), workspace)
  }

  @Test
  fun `application choices that equal the defaults allow reconciliation`(): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    withContext(Dispatchers.EDT) {
      settings.update { it.areInjectionsEnabled = true }
    }

    reconcile(project)

    assertFalse(settings.areInjectionsEnabled)
    assertFalse(settings.state.useCustomStylesheetPath)
  }

  @Test
  fun `a project without saved settings does not complete migration`(): Unit = timeoutRunBlocking {
    reconcile(newProject)

    assertTrue(MarkdownSettings.getInstance().state.areInjectionsEnabled)
    assertFalse(PropertiesComponent.getInstance(newProject).getBoolean("markdown.settings.migrated.v1"))
  }

  private suspend fun reconcile(project: Project): Unit = withContext(Dispatchers.EDT) {
    MarkdownSettings.getInstance().reconcileWithProject(project)
  }
}
