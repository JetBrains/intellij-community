// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.pandoc.PandocApplicationSettings
import org.intellij.plugins.markdown.settings.pandoc.PandocSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

@TestApplication
@Suppress("DEPRECATION")
@SystemProperty(propertyKey = TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY, propertyValue = "false")
class PandocExecutableMigrationTest {
  private val projectFixture = projectFixture(openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false))
  private val project by projectFixture
  private val otherProject by projectFixture(openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false))
  private val externalContentRoot by projectFixture.moduleFixture().sourceRootFixture()
  private val directory by tempPathFixture()
  private lateinit var originalPandocState: PandocApplicationSettings.State
  private lateinit var originalMarkdownState: MarkdownSettingsState

  private val settings get() = PandocApplicationSettings.getInstance()

  @BeforeEach
  fun saveSettings() {
    originalPandocState = settings.state
    originalMarkdownState = MarkdownSettings.getInstance().state
    settings.loadState(PandocApplicationSettings.State())
    MarkdownSettings.getInstance().loadState(MarkdownSettingsState())
  }

  @AfterEach
  fun restoreSettings() {
    settings.loadState(originalPandocState)
    MarkdownSettings.getInstance().loadState(originalMarkdownState)
  }

  @Test
  fun `startup saves an eligible project path as the application choice`(): Unit = timeoutRunBlocking {
    val executable = createFile(directory.resolve("pandoc"))
    configureProject(project, executable.toString())
    MarkdownSettingsMigration().execute(project)
    assertEquals(executable.toString(), settings.pathToPandoc)
    val saved = XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter())
    settings.loadState(XmlSerializer.deserialize(saved, PandocApplicationSettings.State::class.java))
    assertEquals(executable.toString(), settings.resolveExecutable())
  }

  @Test
  fun `null and empty application paths adopt the project path`(): Unit = timeoutRunBlocking {
    val executable = createFile(directory.resolve("pandoc"))
    configureProject(project, executable.toString())
    for (path in listOf(null, "")) {
      settings.pathToPandoc = path
      MarkdownSettingsMigration().execute(project)
      assertEquals(executable.toString(), settings.pathToPandoc)
    }
  }

  @Test
  fun `an existing global choice is preserved`(): Unit = timeoutRunBlocking {
    settings.pathToPandoc = "/chosen/pandoc"
    configureProject(project, createFile(directory.resolve("pandoc")).toString())
    MarkdownSettingsMigration().execute(project)
    assertEquals("/chosen/pandoc", settings.pathToPandoc)
  }

  @Test
  fun `a saved application path takes priority`(): Unit = timeoutRunBlocking {
    settings.loadState(PandocApplicationSettings.State().apply { pathToPandoc = "/saved/pandoc" })
    configureProject(project, createFile(directory.resolve("pandoc")).toString())
    MarkdownSettingsMigration().execute(project)
    assertEquals("/saved/pandoc", settings.pathToPandoc)
  }

  @Test
  fun `the first migrated executable applies to subsequent projects`(): Unit = timeoutRunBlocking {
    val first = createFile(directory.resolve("pandoc-a"))
    val second = createFile(directory.resolve("pandoc-b"))
    configureProject(project, first.toString())
    configureProject(otherProject, second.toString())
    MarkdownSettingsMigration().execute(otherProject)
    MarkdownSettingsMigration().execute(project)
    assertEquals(second.toString(), settings.pathToPandoc)
    assertEquals(second.toString(), settings.resolveExecutable())
  }

  @Test
  fun `a cleared application path can migrate again after serialization`(): Unit = timeoutRunBlocking {
    val first = createFile(directory.resolve("pandoc-a"))
    val second = createFile(directory.resolve("pandoc-b"))
    configureProject(project, first.toString())
    settings.pathToPandoc = first.toString()
    settings.pathToPandoc = null
    val saved = XmlSerializer.serialize(settings.state, SkipDefaultsSerializationFilter())
    settings.loadState(XmlSerializer.deserialize(saved, PandocApplicationSettings.State::class.java))
    configureProject(otherProject, second.toString())
    MarkdownSettingsMigration().execute(project)
    MarkdownSettingsMigration().execute(otherProject)
    assertEquals(first.toString(), settings.pathToPandoc)
  }

  @Test
  fun `an untrusted project cannot supply a legacy executable`(): Unit = timeoutRunBlocking {
    configureProject(project, createFile(directory.resolve("pandoc")).toString(), trusted = false)
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
  }

  @Test
  fun `a skipped project can migrate after it becomes trusted`(): Unit = timeoutRunBlocking {
    val executable = createFile(directory.resolve("pandoc"))
    configureProject(project, executable.toString(), trusted = false)
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
    configureProject(project, executable.toString())
    MarkdownSettingsMigration().execute(project)
    assertEquals(executable.toString(), settings.pathToPandoc)
  }

  @Test
  fun `the trust callback migrates the executable`(): Unit = timeoutRunBlocking {
    val executable = createFile(directory.resolve("pandoc"))
    configureProject(project, executable.toString(), trusted = false)
    val migration = MarkdownSettingsMigration()
    migration.execute(project)
    assertNull(settings.pathToPandoc)

    TrustedProjects.setProjectTrusted(project, true)
    migration.onProjectTrusted(project)
    while (withContext(Dispatchers.EDT) { settings.pathToPandoc == null }) {
      delay(10)
    }
    assertEquals(executable.toString(), settings.pathToPandoc)
  }

  @Test
  fun `a rejected executable does not prevent migration from another project`(): Unit = timeoutRunBlocking {
    val rejected = createFile(Path.of(requireNotNull(project.basePath), "pandoc"))
    val accepted = createFile(directory.resolve("pandoc"))
    configureProject(project, rejected.toString())
    configureProject(otherProject, accepted.toString())

    MarkdownSettingsMigration().execute(project)
    MarkdownSettingsMigration().execute(otherProject)

    assertEquals(accepted.toString(), settings.pathToPandoc)
  }

  @Test
  fun `a path inside the project is not used`(): Unit = timeoutRunBlocking {
    val executable = createFile(Path.of(requireNotNull(project.basePath), "pandoc"))
    configureProject(project, executable.toString())
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
  }

  @Test
  fun `a path in an external content root is not used`(): Unit = timeoutRunBlocking {
    val path = readAction { externalContentRoot.virtualFile.toNioPath().resolve("pandoc") }
    configureProject(project, createFile(path).toString())
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `a symlink outside the project cannot hide an executable inside it`(): Unit = timeoutRunBlocking {
    val executable = createFile(Path.of(requireNotNull(project.basePath), "pandoc"))
    val link = withContext(Dispatchers.IO) { Files.createSymbolicLink(directory.resolve("pandoc-link"), executable) }
    configureProject(project, link.toString())
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `a symlink inside the project is not used even when its target is outside`(): Unit = timeoutRunBlocking {
    val executable = createFile(directory.resolve("pandoc"))
    val link = withContext(Dispatchers.IO) {
      Files.createSymbolicLink(Path.of(requireNotNull(project.basePath), "pandoc-link"), executable)
    }
    configureProject(project, link.toString())
    MarkdownSettingsMigration().execute(project)
    assertNull(settings.pathToPandoc)
  }

  @Test
  fun `empty relative invalid and missing project paths are skipped`(): Unit = timeoutRunBlocking {
    for (path in listOf(null, "", "  ", "pandoc", "tools/pandoc", "\u0000", directory.resolve("missing").toString())) {
      configureProject(project, path)
      MarkdownSettingsMigration().execute(project)
      assertNull(settings.pathToPandoc, path)
    }
  }

  private fun configureProject(project: Project, path: String?, trusted: Boolean = true) {
    TrustedProjects.setProjectTrusted(project, trusted)
    project.service<PandocSettings>().loadState(PandocSettings.State().apply { pathToPandoc = path })
  }

  private suspend fun createFile(path: Path): Path = withContext(Dispatchers.IO) {
    path.writeText("Pandoc migration test")
    path
  }
}
