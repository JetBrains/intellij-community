package com.intellij.ide.starter.path

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

class FrontendIDEDataPaths(
  testHome: Path,
  inMemoryRoot: Path?,
) : IDEDataPaths(testHome, inMemoryRoot) {

  override val eventLogMetadataDir: Path
    get() = System.getProperty("intellij.fus.custom.schema.dir")?.let { Path(it) }
            ?: (systemDir / "frontend" / "per_process_config_0" / "event-log-metadata")

  override val eventLogDataDir: Path
    get() = systemDir / "frontend" / "per_process_system_0" / "event-log-data"

}
