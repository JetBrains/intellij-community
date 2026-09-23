package com.intellij.ide.starter.path

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

class FrontendIDEDataPaths @JvmOverloads constructor(
  testHome: Path,
  inMemoryRoot: Path?,
  sharedSystemDir: Path? = null,
) : IDEDataPaths(testHome, inMemoryRoot, sharedSystemDir) {
  companion object {
    /** The directory a frontend takes for itself, both for its own data below the test and for its reports inside a launch. */
    const val FRONTEND_DIR_NAME: String = "frontend"
  }

  /**
   * A frontend's [testHome] is `<test>/frontend` (see `TestContainer.newContext`), but its reports belong beside the backend's, under
   * `<test>`, so that the frontend and the backend artifacts of one launch land in one place.
   */
  override val reportingRoot: Path
    get() = requireNotNull(testHome.parent) { "A frontend test home must be <test>/$FRONTEND_DIR_NAME, but was $testHome" }

  /**
   * The config directory that the frontend process uses instead of [configDir], see `FrontendProcessPathCustomizer`.
   *
   * The process takes the index 0 when no other frontend process holds that directory.
   */
  val perProcessConfigDir: Path
    get() = systemDir / "frontend" / "per_process_config_0"

  /** The system directory that the frontend process uses instead of [systemDir], see [perProcessConfigDir]. */
  val perProcessSystemDir: Path
    get() = systemDir / "frontend" / "per_process_system_0"

  override val eventLogMetadataDir: Path
    get() = System.getProperty("intellij.fus.custom.schema.dir")?.let { Path(it) }
            ?: (perProcessConfigDir / "event-log-metadata")

  override val eventLogDataDir: Path
    get() = perProcessSystemDir / "event-log-data"

}
