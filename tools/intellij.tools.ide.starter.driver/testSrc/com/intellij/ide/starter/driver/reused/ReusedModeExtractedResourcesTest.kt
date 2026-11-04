package com.intellij.ide.starter.driver.reused

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.PluginId
import com.intellij.driver.sdk.PluginManagerCore
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies that live recovery keeps the plugin resources that `ExtensionsRootType` extracts into `config/extensions`.
 *
 * The first test extracts the resources of the Database plugin. `ResourceVersions` then records the plugin, so the IDE
 * does not extract them again. The second test runs in the same IDE process and must still see the files.
 * In the split mode, the backend extracts them, because the Database plugin of the frontend has no such resources.
 */
@TestMethodOrder(OrderAnnotation::class)
@ReusedRun
class ReusedModeExtractedResourcesTest {
  companion object {
    @delegate:ReusedProject
    val project: CachedProjectConfiguration by lazy {
      ExtractedResourcesRunRegistry.ideProcessId = null
      cachedProjectConfiguration(Files.createTempDirectory("reused-extracted-resources-project"))
    }
  }

  @Test
  @Order(1)
  fun `first test extracts the resources of the database plugin`(driver: Driver, bgRun: BackgroundRun) {
    val resourcesDir = driver.databaseResourcesDir()
    assertFalse(resourcesDir.hasFiles(), "The baseline already holds $resourcesDir")
    ExtractedResourcesRunRegistry.ideProcessId = bgRun.process.id

    val job = driver.updateDatabaseResources()
    waitFor("The extraction of the database resources ends", timeout = 1.minutes) { job.isCompleted() }

    assertTrue(resourcesDir.hasFiles(), "The extraction wrote no file to $resourcesDir")
  }

  @Test
  @Order(2)
  fun `second test still has the extracted resources`(driver: Driver, bgRun: BackgroundRun) {
    assertEquals(ExtractedResourcesRunRegistry.ideProcessId, bgRun.process.id, "Live recovery restarted the IDE")
    val resourcesDir = driver.databaseResourcesDir()
    assertTrue(resourcesDir.hasFiles(), "Live recovery deleted $resourcesDir")
  }
}

@Remote("com.intellij.ide.extensionResources.ExtensionsRootType")
internal interface ExtensionsRootTypeRemote {
  fun getInstance(): ExtensionsRootTypeRemote
  fun updateBundledResources(pluginId: PluginId): JobRemote
}

@Remote("kotlinx.coroutines.Job")
internal interface JobRemote {
  fun isCompleted(): Boolean
}

/** The IDE that extracts the resources: the backend in the split mode. */
private val Driver.extractingTarget: RdTarget
  get() = if (isRemDevMode) RdTarget.BACKEND else RdTarget.DEFAULT

private fun Driver.updateDatabaseResources(): JobRemote {
  val pluginId = utility<PluginId>(extractingTarget).getId(DATABASE_PLUGIN_ID)
  assertNotNull(utility<PluginManagerCore>(extractingTarget).getPlugin(pluginId), "The IDE has no $DATABASE_PLUGIN_ID plugin")
  return utility<ExtensionsRootTypeRemote>(extractingTarget).getInstance().updateBundledResources(pluginId)
}

private fun Driver.databaseResourcesDir(): Path =
  Path.of(utility<PathManagerRemote>(extractingTarget).getConfigPath()).resolve("extensions").resolve(DATABASE_PLUGIN_ID)

private fun Path.hasFiles(): Boolean = exists() && Files.walk(this).use { paths -> paths.anyMatch { it.isRegularFile() } }

private object ExtractedResourcesRunRegistry {
  var ideProcessId: String? = null
}

private const val DATABASE_PLUGIN_ID = "com.intellij.database"
