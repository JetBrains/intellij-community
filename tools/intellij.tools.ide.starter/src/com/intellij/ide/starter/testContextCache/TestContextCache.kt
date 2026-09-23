package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.IDEDataPathsProvider
import com.intellij.ide.starter.runner.Starter.newTestContainer
import com.intellij.ide.starter.runner.targets.TargetIdentifier
import com.intellij.ide.starter.runner.targets.TargetResolver
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * Caches the prepared test context of each project for the whole process.
 *
 * The lookup key is the [CachedProjectConfiguration] instance. The target instance is also part of the key for a
 * non-local target, because the prepared project state is target-specific.
 */
@ApiStatus.Internal
object TestContextCache {
  private val log = Logger.getInstance(TestContextCache::class.java)

  private data class CachedProjectKey(
    val configuration: CachedProjectConfiguration,
    val target: TargetCacheKey, // docker/local
    val runMode: IdeRunMode, // split/monolith
    val contextDiscriminator: Any?, // any other custom, usually not defined
  )

  private val cache: MutableMap<CachedProjectKey, CachedTestContext> = mutableMapOf()
  private val allocatedContextNames: MutableSet<String> = mutableSetOf()

  fun cachedContextDataPathsProvider(): IDEDataPathsProvider = { testName, testDirectory, useInMemoryFileSystem ->
    val testHome = cachedTestDirectory(testDirectory)
    if (testDirectory.name == FRONTEND_TEST_HOME_NAME) {
      IDEDataPaths.createPaths<FrontendIDEDataPaths>(testName, testHome, useInMemoryFileSystem)
    }
    else {
      IDEDataPaths.createPaths<IDEDataPaths>(testName, testHome, useInMemoryFileSystem)
    }
  }

  /**
   * The cache entry that [testContext] was created from, or `null` when it comes from no cached project.
   *
   * For a caller that holds a test context and no configuration, which is every caller that did not create the
   * context. The `system` directory tells the entries apart, see [CachedTestContext.isSourceOf]. The project home
   * cannot: one configuration in the monolith and the split mode has two entries over one unpacked project.
   *
   * A context with an in-memory file system has no `system` link. It falls back to the project home, and only when
   * one entry owns that home.
   */
  fun cachedTestContextOf(testContext: IDETestContext): CachedTestContext? {
    cache.values.firstOrNull { it.isSourceOf(testContext) }?.let { return it }
    val projectHome = testContext._resolvedProjectHome?.toAbsolutePath()?.normalize() ?: return null
    return cache.values.singleOrNull { entry ->
      entry.testContext._resolvedProjectHome?.toAbsolutePath()?.normalize() == projectHome
    }
  }

  /** Returns whether a VFS refresh is owed for the project files of [testContext], and takes that need. */
  fun consumeProjectFilesVfsRefresh(testContext: IDETestContext): Boolean {
    return cachedTestContextOf(testContext)?.consumeProjectFilesVfsRefresh() ?: false
  }

  fun getOrCreate(configuration: CachedProjectConfiguration): CachedTestContext {
    return getOrCreate(configuration, contextDiscriminator = null) { contextName, _ ->
      newTestContainer().newContext(
        testName = contextName,
        testCase = configuration.testCase,
        preserveSystemDir = false,
        projectHome = configuration.testCase.projectInfo.downloadAndUnpackProject(),
        ideDataPathsProvider = cachedContextDataPathsProvider(),
      ).let {
        configuration.setup(it)
      }
    }
  }

  fun getOrCreate(
    configuration: CachedProjectConfiguration,
    contextDiscriminator: Any?,
    createTestContext: (contextName: String, configuration: CachedProjectConfiguration) -> IDETestContext,
  ): CachedTestContext {
    val target = TargetResolver.instance.current
    val targetKey = target.cacheKey()
    val projectKey = CachedProjectKey(configuration, targetKey, IdeRunMode.current(), contextDiscriminator)
    cache[projectKey]?.let { cached ->
      log.warn("Cached context found for $projectKey in cache, retrieving\n" +
               "Re-using cached project snapshot at ${cached.testContext}")
      return cached
    }

    val cachedContextName = nextContextName(configuration.testCase.projectInfo.getName(), targetKey.contextNameSuffix)
    log.warn(
      "Cached context '$cachedContextName' not found in cache, creating new\n" +
      "Creating a new cached context '$cachedContextName'. " +
      "A new CachedProjectConfiguration instance has its own cache entry and is not reused; " +
      "share the same configuration instance when reuse is intended."
    )
    val testContext = createTestContext(cachedContextName, configuration)
    log.info("Creating cached project snapshot at ${testContext.testCase.projectInfo}")
    return CachedTestContext(testContext).also { cache[projectKey] = it }
  }

  /**
   * A directory name for a new cached context, unique in this process. It names the project.
   *
   * One configuration can hold an entry for each run mode and discriminator, and the name carries neither. Two
   * configurations of one project differ only in the suffix.
   */
  private fun nextContextName(projectName: String, targetInstanceName: String?): String {
    val baseName = listOfNotNull(
      projectName,
      targetInstanceName,
    )
      .joinToString(separator = "-")
      .replaceSpecialCharactersWithHyphens()

    if (allocatedContextNames.add(baseName)) {
      return baseName
    }
    var suffix = 2
    var candidate: String
    do {
      candidate = "$baseName-$suffix"
      suffix++
    }
    while (!allocatedContextNames.add(candidate))
    return candidate
  }

  private fun cachedTestDirectory(testDirectory: Path): Path {
    val testsDirectory = GlobalPaths.instance.testsDirectory
    val relativeTestDirectory = testDirectory.relativeTo(testsDirectory)
    return GlobalPaths.instance.localCacheDirectory.resolve(relativeTestDirectory).createDirectories()
  }
}

private data class TargetCacheKey(
  val targetName: String,
  val instanceName: String,
) {
  /** The instance name of a non-local target. The cached context name carries it. */
  val contextNameSuffix: String?
    get() = instanceName.takeUnless { targetName == LOCAL_TARGET_NAME }
}

private fun TargetIdentifier.cacheKey(): TargetCacheKey {
  return TargetCacheKey(targetName = targetName, instanceName = instanceName)
}

//todo: use real constants
private const val LOCAL_TARGET_NAME = "Local"
private const val FRONTEND_TEST_HOME_NAME = "frontend"
