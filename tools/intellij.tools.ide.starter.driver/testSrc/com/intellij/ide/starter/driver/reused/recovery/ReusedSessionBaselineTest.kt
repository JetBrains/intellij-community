package com.intellij.ide.starter.driver.reused.recovery

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.testContextCache.CachedTestContext
import com.intellij.ide.starter.testContextCache.TestContextCache
import com.intellij.ide.starter.testContextCache.cachedProjectConfiguration
import com.intellij.ide.starter.testContextCache.createDummyTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ReusedSessionBaselineTest {
  @Test
  fun `settings recovery plan reloads modified config file`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("settings-reload", tempDir.resolve("runtime"))
    val configFile = context.paths.configDir.resolve("options/editor.xml").apply {
      parent.createDirectories()
      writeText("<editor />")
    }
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      configFile.writeText("<editor changed=\"true\" />")

      val recoveryPlan = baseline.settingsRecoveryPlan(context)

      assertFalse(recoveryPlan.requiresRestart)
      assertEquals(setOf("editor.xml"), recoveryPlan.changedFileSpecs)
      assertTrue(recoveryPlan.deletedFileSpecs.isEmpty())
    }
  }

  @Test
  fun `settings recovery plan restarts for non reloadable config file`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("settings-restart", tempDir.resolve("runtime"))
    val configFile = context.paths.configDir.resolve("options/customCssProvider.xml").apply {
      parent.createDirectories()
      writeText("<CustomCSSProvider />")
    }
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      configFile.writeText("<CustomCSSProvider changed=\"true\" />")

      val recoveryPlan = baseline.settingsRecoveryPlan(context)

      assertTrue(recoveryPlan.requiresRestart)
      assertEquals(setOf("customCssProvider.xml"), recoveryPlan.changedFileSpecs)
    }
  }

  @Test
  fun `settings recovery ignores event log metadata cache`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("event-log-metadata", tempDir.resolve("runtime"))
    val configFile = context.paths.configDir.resolve("options/editor.xml").apply {
      parent.createDirectories()
      writeText("<editor />")
    }
    val metadataFile = context.paths.configDir.resolve("event-log-metadata/fus/dictionaries/python_packages.ndjson").apply {
      parent.createDirectories()
      writeText("baseline metadata")
    }
    val metadataTempFile = metadataFile.resolveSibling("${metadataFile.fileName}.tmp")
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      configFile.writeText("<editor changed=\"true\" />")
      metadataFile.writeText("updated metadata")
      metadataTempFile.writeText("temporary metadata")

      val recoveryPlan = baseline.settingsRecoveryPlan(context)
      baseline.restore(context)

      assertFalse(recoveryPlan.requiresRestart)
      assertEquals(setOf("editor.xml"), recoveryPlan.changedFileSpecs)
      assertEquals("<editor />", configFile.readText())
      assertEquals("updated metadata", metadataFile.readText())
      assertEquals("temporary metadata", metadataTempFile.readText())
    }
  }

  @Test
  fun `an unchanged plugins root is not rebuilt, so a path the baseline cannot hold survives`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("plugins-untouched", tempDir.resolve("runtime"))
    val configFile = context.paths.configDir.resolve("options/editor.xml").apply {
      parent.createDirectories()
      writeText("<editor />")
    }
    val installedPlugin = context.paths.pluginsDir.resolve("some-plugin/lib/some-plugin.jar").apply {
      parent.createDirectories()
      writeText("plugin bytes")
    }
    // A symlink is invisible to the snapshot -- neither a tracked directory nor a regular file -- so the baseline
    // cannot hold it, and only leaving an unchanged root alone preserves it. It stands here for what the live IDE
    // itself cannot survive: the delete half of a rebuild of its own `idea.plugins.path`.
    val linkedPlugin = context.paths.pluginsDir.resolve("linked-plugin")
    Files.createSymbolicLink(linkedPlugin, tempDir.resolve("external-plugin").createDirectories())

    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      configFile.writeText("<editor changed=\"true\" />")

      baseline.restore(context)

      assertEquals("<editor />", configFile.readText(), "the reloadable config root was not restored")
      assertTrue(linkedPlugin.exists(LinkOption.NOFOLLOW_LINKS), "an unchanged plugins root was rebuilt anyway")
      assertEquals("plugin bytes", installedPlugin.readText())
    }
  }

  @Test
  fun `a change under the plugins root requires a restart, so a live restore never rebuilds it`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("plugins-changed", tempDir.resolve("runtime"))
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      context.paths.pluginsDir.resolve("new-plugin/lib/new-plugin.jar").apply {
        parent.createDirectories()
        writeText("plugin bytes")
      }

      assertTrue(baseline.settingsRecoveryPlan(context).requiresRestart)
    }
  }

  /** A test that starts its own IDE can share the cached context and leave its files behind. */
  @Test
  fun `the first start of a reused session goes from the prepared project files`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("reused-after-own-ide", tempDir.resolve("runtime"))
    val sourceFile = context.createProjectFile("src/Main.java", "prepared")
    val cachedTestContext = cachedTestContextOf(context)
    sourceFile.writeText("left by a test that starts its own IDE")

    ReusedSessionBaseline(cachedTestContext).use { baseline ->
      assertTrue(baseline.restoreBeforeStart(context))
      assertEquals("prepared", sourceFile.readText())
    }
  }

  /** The baseline keeps what the IDE wrote at the open, so a restart does not import the project again. */
  @Test
  fun `a later start of a reused session goes from the opened project files`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("reused-restart", tempDir.resolve("runtime"))
    val sourceFile = context.createProjectFile("src/Main.java", "prepared")
    val importedFile = context.createProjectFile(".idea/modules.xml", "")

    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      importedFile.writeText("<modules />")
      baseline.capture(context, emptyList(), emptyList())
      sourceFile.writeText("left by a reused test")

      assertTrue(baseline.restoreBeforeStart(context))
      assertEquals("prepared", sourceFile.readText())
      assertEquals("<modules />", importedFile.readText())
    }
  }

  /** A reused session can share the cached context and leave its files behind. */
  @Test
  fun `a test that starts its own IDE after a reused session goes from the prepared project files`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("own-ide-after-reused", tempDir.resolve("runtime"))
    val sourceFile = context.createProjectFile("src/Main.java", "prepared")
    val cachedTestContext = cachedTestContextOf(context)

    ReusedSessionBaseline(cachedTestContext).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      sourceFile.writeText("left by a reused test")

      assertTrue(cachedTestContext.restorePreparedProjectFiles())
      assertEquals("prepared", sourceFile.readText())
    }
  }

  /** The IDE reads the disabled plugins only at its start, so a live reload would leave the change in memory. */
  @Test
  fun `a change to the disabled plugins requires a restart`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("disabled-plugins", tempDir.resolve("runtime"))
    val disabledPluginsFile = context.paths.configDir.resolve("disabled_plugins.txt").apply {
      parent.createDirectories()
      writeText("baseline")
    }
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      disabledPluginsFile.writeText("changed")

      assertTrue(baseline.settingsRecoveryPlan(context).requiresRestart)
    }
  }

  /** The lock belongs to the running IDE, so a restore must not write the PID of an earlier IDE into it. */
  @Test
  fun `the lock file of the running IDE is neither tracked nor restored`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("lock-file", tempDir.resolve("runtime"))
    val lockFile = context.paths.configDir.resolve(".lock").apply {
      parent.createDirectories()
      writeText("1")
    }
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      lockFile.writeText("2")

      val recoveryPlan = baseline.settingsRecoveryPlan(context)
      assertFalse(recoveryPlan.requiresRestart)
      assertTrue(recoveryPlan.changedFileSpecs.isEmpty())

      baseline.restore(context)
      assertEquals("2", lockFile.readText())
    }
  }

  /** The IDE holds the database open, so a reused session never rewinds it, with or without a restart. */
  @Test
  fun `a config database of the running IDE is neither tracked nor restored`(@TempDir tempDir: Path) {
    val context = createDummyTestContext("config-database", tempDir.resolve("runtime"))
    val databaseFile = context.paths.configDir.resolve("app-internal-state.db").apply {
      parent.createDirectories()
      writeText("baseline")
    }
    ReusedSessionBaseline(cachedTestContextOf(context)).use { baseline ->
      baseline.capture(context, emptyList(), emptyList())
      databaseFile.writeText("changed")

      val recoveryPlan = baseline.settingsRecoveryPlan(context)
      assertFalse(recoveryPlan.requiresRestart)
      assertTrue(recoveryPlan.changedFileSpecs.isEmpty())

      baseline.restore(context)
      assertEquals("changed", databaseFile.readText())
    }
  }
}

private fun IDETestContext.createProjectFile(relativePath: String, content: String): Path {
  return resolvedProjectHome.resolve(relativePath).apply {
    parent.createDirectories()
    writeText(content)
  }
}

/** A new cache entry over [context]. A new configuration instance is a new entry, so no test shares it. */
private fun cachedTestContextOf(context: IDETestContext): CachedTestContext {
  return TestContextCache.getOrCreate(cachedProjectConfiguration(context.resolvedProjectHome), contextDiscriminator = null) { _, _ -> context }
}
