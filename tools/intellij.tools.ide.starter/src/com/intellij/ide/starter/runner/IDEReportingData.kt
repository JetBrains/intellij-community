package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.isRemDevContext
import com.intellij.ide.starter.report.DetailsOnCI
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.dirName
import com.intellij.ide.starter.utils.ReportingPathUtils.checkPathLength
import com.intellij.ide.starter.utils.hyphenateTestName
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name

@ApiStatus.Internal
class IDEReportingData internal constructor(
  private val providedTestName: String,
  private val launchName: String? = null,
  testMethod: TestMethodData? = null,
  testHome: Path,
  isFrontend: Boolean = false,
) {
  internal data class TestMethodData(
    val className: String,
    val displayName: String,
    val index: Int,
  )

  companion object {
    internal fun artifactNameWithIdeRole(testContext: IDETestContext, artifactName: String): String = when {
      testContext.testCase.ideInfo.isFrontend -> "$artifactName-frontend"
      testContext.isRemDevContext() -> "$artifactName-backend"
      else -> artifactName
    }

    /**
     * Builds a class-name directory and an indexed display-name directory.
     *
     * The first slash in [testMethodName] is the class/display boundary. Parameterized display names may contain more slashes
     * (for example, Rust source paths), but those are test metadata and must not create nested artifact directories.
     */
    private fun dirNameOf(testMethodName: String, index: Int): String {
      val indexPrefix = "${index}_"
      val segments = testMethodName.split('/', limit = 2)
      return segments.mapIndexed { segmentIndex, segment ->
        val isIndexedSegment = segmentIndex == segments.lastIndex
        val prefix = if (isIndexedSegment) indexPrefix else ""
        val singlePathSegment = if (isIndexedSegment) segment.replace('/', '-') else segment
        val pathSafeSegment = when (singlePathSegment) {
          "." -> "%2E"
          ".." -> "%2E%2E"
          else -> singlePathSegment
        }
        dirName(pathSafeSegment, prefix)
      }.joinToString("/")
    }
  }

  private val testRoot: Path = if (isFrontend) testHome.parent else testHome
  private val testMethodArtifactName: String? = testMethod?.run {
    listOf(className, displayName).filter { it.isNotEmpty() }.joinToString("/").hyphenateTestName()
  }
  private val testMethodArtifactDirName: String? = testMethod?.let { dirNameOf(requireNotNull(testMethodArtifactName), it.index) }

  /**
   * We want the test reporting dir to be `/work/test/method/launch/frontend`, rather than
   * `/work/test/frontend/method/launch`, so frontend and backend artifacts are in the same place.
   */
  private val launchDir: Path = listOfNotNull(
    testMethodArtifactDirName,
    launchName?.let { dirName(it) },
    "frontend".takeIf { isFrontend },
  ).fold(testRoot) { path, segment -> path.resolve(segment) }

  val reportsDir: Path = createReportingDirectory("reports")
  val snapshotsDir: Path = createReportingDirectory("snapshots")
  val logsDir: Path = createReportingDirectory("log")

  val jbrDiagnostic: Path = logsDir.resolve("jbrDiagnostic")

  private fun createReportingDirectory(name: String): Path =
    checkPathLength(launchDir.resolve(name)).createDirectories()

  /** The CI-safe path the artifacts of this launch are published under. */
  val artifactPath: String = run {
    val testPath = testMethodArtifactName?.let { methodArtifactName ->
      val methodDirName = requireNotNull(testMethodArtifactDirName)
      val hyphenatedProvidedTestName = providedTestName.hyphenateTestName()

      if (hyphenatedProvidedTestName.contains(methodArtifactName)) {
        hyphenatedProvidedTestName.replaceFirst(methodArtifactName, methodDirName)
      }
      else {
        "$providedTestName/$methodDirName"
      }
    } ?: providedTestName

    listOfNotNull(testPath, launchName)
      .filter { it.isNotEmpty() }
      .joinToString("/")
      .replaceSpecialCharactersWithHyphens()
  }

  val humanReadableTestName: String = buildList {
    if (testMethod == null) {
      add(providedTestName)
    }
    else {
      add(testMethod.className)
      add(testMethod.displayName)
    }
    launchName?.let(::add)
  }.filter { it.isNotEmpty() }.joinToString("/")

  init {
    reportArtifactsLink("Link to Logs and artifacts", this)
  }

  internal fun reportStartupArtifactsLink(startupReportingData: IDEReportingData) {
    startupReportingData.takeUnless { it.artifactPath == artifactPath }?.let {
      reportArtifactsLink("Link to Logs and artifacts (IDE Startup)", it)
    }
  }

  internal fun publishArtifacts(testContext: IDETestContext) {
    runCatching {
      publishArtifact(testContext, logsDir, "logs")
      publishArtifact(testContext, snapshotsDir, "snapshots")
      publishArtifact(testContext, reportsDir, "reports")
    }.onFailure {
      logError("Fail to publish artifacts for $humanReadableTestName", it)
    }.onSuccess {
      logOutput("Successfully published artifacts for $humanReadableTestName")
    }
  }

  internal fun publishArtifact(testContext: IDETestContext, source: Path, artifactName: String) {
    testContext.publishArtifact(
      source = source,
      artifactPath = artifactPath,
      artifactName = ReportingPathUtils.formatArtifactName(
        artifactNameWithIdeRole(testContext, artifactName),
        artifactPath,
      ),
    )
  }

  private fun reportArtifactsLink(name: String, reportingData: IDEReportingData) {
    val link = DetailsOnCI.instance.getLinkToCIArtifacts(reportingData) ?: return
    TeamCityReporter.reportTestMetadata(
      testName = null,
      type = TeamCityReporter.MetadataType.LINK,
      flowId = null,
      name = name,
      value = link,
    )
  }

  internal fun collectJBRDiagnosticFiles(javaProcessId: Long) {
    if (javaProcessId == 0L) return
    val userHome = Path.of(System.getProperty("user.home"))
    listOf(
      userHome.resolve("java_error_in_idea_$javaProcessId.log"),
      userHome.resolve("jbr_err_pid$javaProcessId.log"),
    ).filter { it.exists() }.forEach { crashFile ->
      crashFile.copyTo(jbrDiagnostic.createDirectories().resolve(crashFile.name), overwrite = true)
    }
  }

  @Volatile
  var allowedIdeErrorReportFiles: Set<Path>? = null
    private set

  fun restrictIdeErrorReportsToExistingFiles() {
    allowedIdeErrorReportFiles = collectIdeErrorReportFiles()
  }

  private fun collectIdeErrorReportFiles(): Set<Path> {
    if (!logsDir.exists()) return emptySet()
    return Files.find(logsDir, 4, { path, _ -> path.isIdeErrorMessageFile() }).use { stream ->
      stream.map { it.toAbsolutePath().normalize() }.toList().toSet()
    }
  }

  private fun Path.isIdeErrorMessageFile(): Boolean {
    val reportRootName = parent?.parent?.name
    return name == ErrorReporter.MESSAGE_FILENAME &&
           (reportRootName == ErrorReporter.ERRORS_DIR_NAME || reportRootName == "script-${ErrorReporter.ERRORS_DIR_NAME}")
  }
}
