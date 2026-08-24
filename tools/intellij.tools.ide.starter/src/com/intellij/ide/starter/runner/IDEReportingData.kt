package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.checkPathLength
import com.intellij.ide.starter.utils.flattened
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

/**
 * Where one IDE launch reports, and what it is called. A launch is made of a test, the test method it belongs to, a launch name, and
 * whether it is the frontend of a split-mode pair. The three names below spell those out differently, because a local directory, a CI
 * artifact path and a line in a test report allow different things:
 *
 * |          | reporting directories          | [artifactPath] in a reused IDE                     | [humanReadableTestName]       |
 * |----------|--------------------------------|----------------------------------------------------|-------------------------------|
 * | test     | absent, the root is it         | spelled out, unless the method doubles it          | raw, unless a method is known |
 * | method   | hyphenated, bounded, indexed   | the same, plus the class when the test is left out | raw `class/method`            |
 * | launch   | its last level, hashed, bounded| the same as on the left                            | raw and whole                 |
 * | frontend | an extra `frontend` segment    | the same as on the left                            | absent                        |
 *
 * Reporting directories on disk:
 * `<test>/<class>/<index>_<method>/<launch>/frontend`, each level only when it applies. To save path length, no level is spelled twice: the
 * class is left out when the test's own directory names it, and the launch keeps only the last level of its name, plus a hash of the rest,
 * see [launchDirName].
 *
 * Published paths:
 * `<test>/<launch>` for the launch an IDE starts out with — the legacy path IJPerf can rebuild, see [legacyArtifactPath] — and
 * `<test>/<class>/<index>_<method>/<launch>` for every launch registered for it after that, following the directories on disk, see
 * [reusedIdeArtifactPath]. [artifactPath] is whichever of the two this launch was constructed with.
 *
 * A launch creates its directories as it is constructed, so a path over [ReportingPathUtils.PATH_LENGTH_LIMIT] is reported right there.
 * The directories are created anyway, see [ReportingPathUtils.checkPathLength].
 */
@ApiStatus.Internal
class IDEReportingData internal constructor(
  reportingRoot: Path,
  private val testName: String,
  private val testMethod: TestMethodReportingIdentity? = null,
  private val launchName: String? = null,
  private val isFrontend: Boolean = false,
  internal val artifactLayout: ArtifactLayout = ArtifactLayout.LEGACY,
) {
  internal enum class ArtifactLayout {
    LEGACY,
    REUSED_IDE,
  }

  // region Names

  private val flattenedTestName: String = testName.flattened()
  private val flattenedLaunchName: String? = launchName?.takeUnless(String::isEmpty)?.flattened()

  private val launchDirName: String? = launchName
    ?.takeUnless { it.isEmpty() || testMethod?.namesTheLaunch(flattenedLaunchName) == true }
    ?.let(ReportingPathUtils::launchDirNameOf)

  /**
   * One directory name per level this launch reports under: `<class>/<index>_<method>/<launch>/frontend`, each level only when it applies.
   * Every one of them is bounded, because the whole path has to stay within [ReportingPathUtils.PATH_LENGTH_LIMIT].
   */
  private fun dirSegmentsBelowTest(theClassIsNamedAbove: Boolean): List<String> = buildList {
    testMethod?.dirNames(theClassIsNamedAbove)?.let(::addAll)
    launchDirName?.let(::add)
    if (isFrontend) add(FrontendIDEDataPaths.FRONTEND_DIR_NAME)
  }

  /**
   * The reporting directories copied below the test: a launch reporting into `<class>/<index>_<method>/<launch>` publishes into
   * `<test>/<class>/<index>_<method>/<launch>`. The test is left out when the method names it, and the class then stays to name it instead,
   * so that one of the two always names the test.
   */
  private val reusedIdeArtifactPath: String = artifactPathOf(buildList {
    if (testMethod?.namesTheTest(flattenedTestName) != true) {
      add(ReportingPathUtils.testDirectoryName(testName))
    }
    addAll(dirSegmentsBelowTest(theClassIsNamedAbove =
                                  testMethod?.hasItsClassNamedBy(flattenedTestName) == true
                                  && !testMethod.namesTheTest(flattenedTestName)))
  })

  /**
   * The `<test>/<launch>` path IJPerf can rebuild, for an IDE used by one test only. Performance tests are not expected to reuse an IDE, so
   * this is the path their artifacts keep. The launch name goes in as it was given, slashes and all, being a path of its own. A standalone
   * split-mode frontend shares it with its backend, prefixing its artifact names with `frontend-` instead, which is what IJPerf needs too.
   */
  private val legacyArtifactPath: String = artifactPathOf(listOfNotNull(testName, launchName))

  /**
   * [legacyArtifactPath] for the launch an IDE starts out with, [reusedIdeArtifactPath] for every launch registered for it after that.
   * Which of the two this launch publishes into was settled when it was constructed: the first one keeps the legacy path even once the
   * IDE has been reused, which is why `IDEReportingDataRegistry` publishes a link of its own for it.
   */
  val artifactPath: String
    get() = artifactPathFor(artifactLayout)

  /** What a test report calls this launch: the names as they were given, neither hyphenated nor bounded. */
  val humanReadableTestName: String = buildList {
    if (testMethod == null) {
      add(testName)
    }
    else {
      addAll(testMethod.rawSegments)
    }
    if (testMethod?.namesTheLaunch(flattenedLaunchName) != true) {
      launchName?.let(::add)
    }
  }.filter(String::isNotEmpty).joinToString("/")

  /**
   * Segments spelled the way a published path is: hyphenated once joined rather than segment by segment, so a segment may keep a trailing
   * hyphen; that is what the published artifacts have already, and changing it moves every artifact URL there is.
   */
  private fun artifactPathOf(segments: List<String>): String =
    segments.filter(String::isNotEmpty).joinToString("/").replaceSpecialCharactersWithHyphens()

  // endregion

  // region Reporting directories

  private val launchReportingDir: Path =
    dirSegmentsBelowTest(theClassIsNamedAbove = testMethod?.hasItsClassNamedBy(flattenedTestName) == true)
      .fold(reportingRoot) { dir, segment -> dir.resolve(segment) }

  val reportsDir: Path = createReportingDirectory("reports")
  val snapshotsDir: Path = createReportingDirectory("snapshots")
  val logsDir: Path = createReportingDirectory("log")

  /**
   * Where the JVM of this launch writes its crash log if it crashes. Only named here; whoever points the JVM at it creates it, so an empty
   * directory never looks like a lost crash log. That same writer checks the directory against
   * [ReportingPathUtils.WIDEST_CRASH_LOG_NAME], because the name is only worth reserving once something is about to write it.
   */
  val jvmCrashLogDir: Path = logsDir.resolve("jvm-crash")

  /** Where the crash logs this launch left in the home directory are copied, if it left any. Created on demand, like [jvmCrashLogDir]. */
  val jbrDiagnosticDir: Path = logsDir.resolve("jbrDiagnostic")

  private fun createReportingDirectory(name: String): Path =
    checkPathLength(launchReportingDir.resolve(name)).createDirectories()

  // endregion

  // region Artifact publication

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
    val publishedArtifactType = if (artifactLayout == ArtifactLayout.LEGACY && isFrontend)
    // precisely this way, required by Ij Perf
      "$artifactName-frontend"
    else artifactName
    testContext.publishArtifact(
      source = source,
      artifactPath = artifactPathFor(artifactLayout),
      artifactName = ReportingPathUtils.formatArtifactName(publishedArtifactType),
    )
  }

  private fun artifactPathFor(layout: ArtifactLayout): String = when (layout) {
    ArtifactLayout.LEGACY -> legacyArtifactPath
    ArtifactLayout.REUSED_IDE -> reusedIdeArtifactPath
  }

  // endregion

  // region IDE diagnostics

  /**
   * Which IDE error reports under [logsDir] this launch answers for. `null`, the starting state, means all of them; a set means only those,
   * empty included. `BackgroundRun.forceKill` records the reports written so far through [restrictIdeErrorReportsToExistingFiles], so what
   * the IDE logs while being killed is not blamed on the test.
   */
  @Volatile
  var allowedIdeErrorReportFiles: Set<Path>? = null
    private set

  internal fun collectJBRDiagnosticFiles(javaProcessId: Long) {
    if (javaProcessId == 0L) return
    val userHome = Path.of(System.getProperty("user.home"))
    listOf(
      userHome.resolve("java_error_in_idea_$javaProcessId.log"),
      userHome.resolve("jbr_err_pid$javaProcessId.log"),
    ).filter { it.exists() }.forEach { crashFile ->
      crashFile.copyTo(jbrDiagnosticDir.createDirectories().resolve(crashFile.name), overwrite = true)
    }
  }

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

  // endregion

  override fun toString(): String = "Reporting data of $humanReadableTestName at $launchReportingDir"
}
