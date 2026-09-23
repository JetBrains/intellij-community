package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IDEStartConfig
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.util.system.OS
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal val reusableTestIdeInfo = IdeInfo(
  productCode = "IU",
  platformPrefix = "idea",
  executableFileName = "idea",
  fullName = "IntelliJ IDEA",
)

// Tests in this package are deliberately creating new CachedProjectConfiguration to check the CachedProjectConfiguration flow without cache hits.
// In real tests that is a bad practice as we want to have as many cache hits as possible.
fun cachedProjectConfiguration(
  projectDir: Path,
  useRelease: Boolean = false,
  additionalSetup: IDETestContext.() -> Unit = {},
): CachedProjectConfiguration {
  val testCase = TestCase(
    reusableTestIdeInfo,
    LocalProjectInfo(
      projectDir = projectDir.createDirectories(),
      isReusable = true,
    ),
  ).let { if (useRelease) it.useRelease() else it }
  return CachedProjectConfiguration(
    testCase = testCase,
    setup = {
      applyVMOptionsPatch { addSystemProperty("idea.trust.all.projects", true) }
      additionalSetup()
      this
    },
  )
}

fun createDummyTestContext(
  testName: String,
  testHome: Path,
  projectHome: Path = testHome.resolve("project-home").createDirectories(),
  sharedSystemDir: Path? = null,
): IDETestContext {
  testHome.createDirectories()
  return IDETestContext(
    paths = IDEDataPaths(testHome, null, sharedSystemDir),
    ide = DummyInstalledIde(testHome.resolve("ide-home").createDirectories()),
    testCase = TestCase(
      reusableTestIdeInfo,
      LocalProjectInfo(
        projectDir = testHome.resolve("project-under-test").createDirectories(),
        isReusable = true,
      ),
    ).useRelease(),
    testName = testName,
    _resolvedProjectHome = projectHome,
    publishers = emptyList(),
  )
}

internal class DummyInstalledIde(private val home: Path) : InstalledIde {
  private val vmOptionsFile = home.resolve("idea.vmoptions").createFile().apply { writeText("") }

  override val vmOptions: VMOptions = VMOptions.readIdeVMOptions(this, vmOptionsFile)
  override val build: String = "0.0"
  override val os: OS = OS.macOS
  override val productCode: String = "IU"
  override val isFromSources: Boolean = false
  override val installationPath: Path = home

  override fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig {
    return object : IDEStartConfig {
      override val workDir: Path = logsDir
      override val commandLine: List<String> = emptyList()
    }
  }

  override suspend fun resolveAndDownloadTheSameJDK(): Path = home
}

/**
 * Runs [block] with dedicated, [testCaseId]-scoped cached/current test home directories, deleted before and after so
 * tests never see leftover state from each other or from a previous run.
 *
 * They live under the real [GlobalPaths] roots rather than a `@TempDir` because
 * [CachedTestContext.createCurrentTestContext] requires the cached context to sit under the local cache directory.
 */
@OptIn(ExperimentalPathApi::class)
internal fun withTestHomes(
  ownerName: String,
  testCaseId: String,
  block: (cachedTestHome: Path, currentTestHomeA: Path, currentTestHomeB: Path) -> Unit,
) {
  val cachedTestHome = GlobalPaths.instance.localCacheDirectory.resolve("$ownerName-cached-$testCaseId")
  val currentTestHomeA = GlobalPaths.instance.testsDirectory.resolve("$ownerName-current-a-$testCaseId")
  val currentTestHomeB = GlobalPaths.instance.testsDirectory.resolve("$ownerName-current-b-$testCaseId")
  val allTestHomes = listOf(currentTestHomeA, currentTestHomeB, cachedTestHome)

  fun cleanup() {
    // Deleting many small index files right after they were created can transiently race with the OS
    // (observed as spurious "directory not empty" failures), so retry a few times before giving up.
    allTestHomes.forEach { dir ->
      repeat(5) { attempt ->
        val failure = runCatching {
          if (dir.exists()) {
            dir.deleteRecursively()
          }
        }.exceptionOrNull()
        if (failure == null) return@forEach
        if (attempt == 4) throw failure
        Thread.sleep(100)
      }
    }
  }

  cleanup()
  try {
    block(cachedTestHome, currentTestHomeA, currentTestHomeB)
  }
  finally {
    cleanup()
  }
}
