package com.intellij.ide.starter.path

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

const val TEST_DATA_CACHE_NAME = "test-data-cache"

const val IDE_TESTS_SUBSTRING: String = "ide-tests"

abstract class GlobalPaths(val checkoutDir: Path) {
  /**
   * `<checkout>/out`, or the directory the system property `ide.starter.out.dir` names, for a checkout that is mounted
   * read-only.
   */
  val intelliJOutDirectory: Path =
    System.getProperty("ide.starter.out.dir")?.let(Path::of) ?: (checkoutDir.toAbsolutePath() / "out")
  val artifactsDirectory: Path = intelliJOutDirectory / "artifacts"

  /**
   * Local => out
   * CI => out/tests
   */
  val compiledRootDirectory: Path = when (CIServer.instance.isBuildRunningOnCI) {
    true -> intelliJOutDirectory / "tests"
    false -> intelliJOutDirectory // Local run
  }

  open val testHomePath: Path = intelliJOutDirectory.resolve(IDE_TESTS_SUBSTRING).createDirectories()

  init {
    stopEditorConfigLookup(testHomePath)
  }

  open val devServerDirectory: Path = intelliJOutDirectory.resolve("dev-run").createDirectories()

  val installersDirectory = (testHomePath / "installers").createDirectories()

  val testsDirectory = (testHomePath / "tests").createDirectories()

  /** Cache directory on the current machine */
  open val localCacheDirectory: Path = if (CIServer.instance.isBuildRunningOnCI &&
                                           !System.getProperty("agent.persistent.cache").isNullOrEmpty()) {
    (Paths.get(System.getProperty("agent.persistent.cache"), TEST_DATA_CACHE_NAME)).createDirectories()
  }
  else {
    (testHomePath / "cache").createDirectories()
  }

  /** Returns local cache directory for the entity */
  fun getLocalCacheDirectoryFor(entity: String): Path = (localCacheDirectory / entity).createDirectories()

  /** Returns cache directory for the entity (probably on the target environment. Eg: in Docker */
  open fun getCacheDirectoryFor(entity: String): Path = getLocalCacheDirectoryFor(entity)

  open val cacheDirForProjects: Path get() = getCacheDirectoryFor("projects")

  companion object {
    val instance: GlobalPaths
      get() = di.direct.instance<GlobalPaths>()
  }
}

/**
 * Writes a `.editorconfig` with only `root = true` to [directory], so that EditorConfig does not read the files above it.
 * A local run keeps the test projects inside the checkout, and the `.editorconfig` of the checkout would change their code style.
 * An existing file stays as it is.
 */
internal fun stopEditorConfigLookup(directory: Path) {
  try {
    directory.createDirectories().resolve(".editorconfig").writeText("root = true\n", Charsets.UTF_8, StandardOpenOption.CREATE_NEW)
  }
  catch (_: FileAlreadyExistsException) {
    // Another run or the user wrote it first.
  }
}
