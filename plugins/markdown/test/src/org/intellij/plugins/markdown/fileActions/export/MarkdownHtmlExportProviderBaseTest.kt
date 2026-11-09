// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox

@TestApplication
internal class MarkdownHtmlExportProviderBaseTest {
  private val projectFixture = projectFixture(
    openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false),
    openAfterCreation = true,
  )
  private val sourceRoot = projectFixture.moduleFixture("src").sourceRootFixture()
  private val mdFile = sourceRoot.psiFileFixture("README.md", "# Title\n")

  private val settings: MarkdownHtmlExportSettings
    get() = service<MarkdownHtmlExportSettings>()

  private var initialSettings = HtmlResourceSavingSettings(isSaved = false, resourceDir = "")

  @BeforeEach
  fun rememberSettings() {
    initialSettings = settings.getResourceSavingSettings()
  }

  @AfterEach
  fun restoreSettings() {
    settings.saveResources = initialSettings.isSaved
    settings.resourceDirectory = initialSettings.resourceDir
  }

  @Test
  fun `a cleared checkbox makes the export embed the images`(): Unit = timeoutRunBlocking {
    settings.saveResources = true
    val provider = RecordingProvider()

    withContext(Dispatchers.EDT) {
      val checkBox = createSaveImagesCheckBox(provider)
      assertTrue(checkBox.isSelected)
      checkBox.isSelected = false
      provider.exportFile(projectFixture.get(), mdFile.get().virtualFile, "README.html")
    }

    assertFalse(provider.exportedSettings.single().isSaved)
    assertFalse(settings.saveResources)
  }

  @Test
  fun `a selected checkbox makes the export save the images`(): Unit = timeoutRunBlocking {
    settings.saveResources = false
    val provider = RecordingProvider()

    withContext(Dispatchers.EDT) {
      val checkBox = createSaveImagesCheckBox(provider)
      assertFalse(checkBox.isSelected)
      checkBox.isSelected = true
      provider.exportFile(projectFixture.get(), mdFile.get().virtualFile, "README.html")
    }

    assertTrue(provider.exportedSettings.single().isSaved)
    assertTrue(settings.saveResources)
  }

  @Test
  fun `an untouched checkbox keeps the saved choice`(): Unit = timeoutRunBlocking {
    settings.saveResources = false
    val provider = RecordingProvider()

    withContext(Dispatchers.EDT) {
      createSaveImagesCheckBox(provider)
      provider.exportFile(projectFixture.get(), mdFile.get().virtualFile, "README.html")
    }

    assertEquals(listOf(false), provider.exportedSettings.map { it.isSaved })
    assertFalse(settings.saveResources)
  }

  private fun createSaveImagesCheckBox(provider: RecordingProvider): JCheckBox {
    val component = panel {
      with(provider) {
        createSettingsComponent(projectFixture.get(), mdFile.get().virtualFile)
      }
    }
    return UIUtil.findComponentsOfType(component, JCheckBox::class.java).single()
  }

  private class RecordingProvider : MarkdownHtmlExportProviderBase() {
    val exportedSettings = mutableListOf<HtmlResourceSavingSettings>()

    override fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String, savingSettings: HtmlResourceSavingSettings) {
      exportedSettings.add(savingSettings)
    }

    override fun validate(project: Project, file: VirtualFile): String? = null
  }
}
