package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.dirName
import com.intellij.ide.starter.utils.ReportingPathUtils.checkPathLength
import com.intellij.ide.starter.utils.hyphenateTestName
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

/** The test method an IDE launch belongs to, and which activation of that method within one IDE process it is. */
@ApiStatus.Internal
data class TestMethodIdentity(
  val className: String,
  val displayName: String,
  /** 1-based order of this method's first activation within one IDE process; prefixes its reporting directory. */
  val executionIndex: Int,
) {
  /** What the method is called, one level at a time: the class it is in and the method itself, each only when it is known. */
  val nameSegments: List<String> = listOf(className, displayName).filter(String::isNotEmpty)
}

/**
 * Where one IDE launch reports, and what that launch is called. A launch is a test, the test method it belongs to, a launch name and
 * whether it is the frontend of a split-mode pair — spelled three ways, because a local directory, a CI artifact path and a line in a test
 * report each accept different things:
 *
 * |          | reporting directories          | [artifactPath]                            | [humanReadableTestName]       |
 * |----------|--------------------------------|-------------------------------------------|-------------------------------|
 * | test     | absent, the root already is it | spelled out, unless the method doubles it | raw, unless a method is known |
 * | method   | hyphenated, bounded, indexed   | the very same segments                    | raw `class/method`            |
 * | launch   | bounded with a hash suffix     | the very same segment                     | raw                           |
 * | frontend | an extra `frontend` segment    | the very same segment                     | absent                        |
 *
 * Below the test, [artifactPath] is the reporting directories verbatim, so what a launch publishes is shaped the way what it collected is:
 * a launch reporting into `<class>/<index>_<method>/<launch>` publishes into `<test>/<class>/<index>_<method>/<launch>`. [artifactPath] is
 * hyphenated once it has been joined rather than segment by segment, so a segment may keep a trailing hyphen; that is what the published
 * artifacts have already, and changing it moves every existing artifact URL.
 *
 * No level is spelled twice: the class is left out when the test's own directory already names it, and the test is left out of
 * [artifactPath] when the method segments already spell it. Exactly one of the two applies, so that something always names the test.
 *
 * A launch takes its directories as it is constructed, so a resulting path that would not fit within [ReportingPathUtils.PATH_LENGTH_LIMIT]
 * is reported as this is constructed. The directories are created all the same — see [ReportingPathUtils.checkPathLength].
 */
@ApiStatus.Internal
class IDEReportingData internal constructor(
  reportingRoot: Path,
  private val testName: String,
  private val testMethod: TestMethodIdentity? = null,
  requestedLaunchName: String? = null,
  private val isFrontend: Boolean = false,
) {
  // region Names

  /** `null` when the launch has no name of its own: none was requested, it was empty, or it is the test method's name again. */
  private val launchName: String? = requestedLaunchName
    ?.takeUnless { it.isEmpty() }
    ?.takeUnless { it == testMethod?.displayName?.hyphenateTestName() }

  /**
   * Whether [testName] already spells the test method out, which is what a name taken from `CurrentTestMethod` does — then [artifactPath]
   * leaves the test out instead of doubling it. With no test method there is nothing to double.
   */
  private val testNameSpellsTheMethodOut: Boolean =
    testMethod != null && testName.hyphenateTestName() == testMethod.nameSegments.joinToString("/").hyphenateTestName()

  /**
   * Whether the test's own reporting directory already names the class the method is in, which is what a test name derived from
   * `CurrentTestMethod` does — even when a suffix of its own, a product code say, keeps it from spelling the whole method out. Repeating
   * the class below that directory spends a bounded name on what the level above says already, and cutting both to the same length leaves
   * two directories that look alike without being alike. Never together with [testNameSpellsTheMethodOut], which leaves the naming of the
   * test to the class.
   */
  private val testNameSpellsTheClassOut: Boolean = !testNameSpellsTheMethodOut && testMethod?.className
    ?.takeUnless(String::isEmpty)
    ?.hyphenateTestName()
    ?.let { hyphenatedClassName ->
      val hyphenatedTestName = testName.hyphenateTestName()
      // up to a separator only, so that a class the test name merely begins like is still spelled out
      hyphenatedTestName.startsWith(hyphenatedClassName) &&
      hyphenatedTestName.getOrNull(hyphenatedClassName.length)?.isLetterOrDigit() != true
    } == true

  /**
   * One directory name per level the method occupies, the last of them prefixed with the execution index so that the order the methods ran
   * in is visible in the reporting tree. Shared verbatim with [artifactPath].
   */
  private val testMethodDirSegments: List<String> = testMethod?.run {
    val hyphenatedSegments = nameSegments.map { it.hyphenateTestName() }
    // the class only when the root does not name it already, and never when it is the only segment: the last one carries the index
    val segments = if (testNameSpellsTheClassOut && hyphenatedSegments.size > 1) hyphenatedSegments.drop(1) else hyphenatedSegments
    segments.mapIndexed { segmentIndex, segment ->
      val isIndexedSegment = segmentIndex == segments.lastIndex
      // a parameterized display name may contain slashes, which have to stay inside the one directory the method gets
      val singlePathSegment = if (isIndexedSegment) segment.replace('/', '-') else segment
      val pathSafeSegment = when (singlePathSegment) {
        "." -> "%2E"
        ".." -> "%2E%2E"
        else -> singlePathSegment
      }
      dirName(pathSafeSegment, prefix = if (isIndexedSegment) "${executionIndex}_" else "")
    }
  }.orEmpty()

  /**
   * One directory name per level this launch reports below the reporting root of its test — `<class>/<index>_<method>/<launch>/frontend`,
   * each level only when it applies. Every segment is bounded, because the complete path has to stay within
   * [ReportingPathUtils.PATH_LENGTH_LIMIT].
   */
  private val reportingDirSegments: List<String> = buildList {
    addAll(testMethodDirSegments)
    launchName?.let { add(dirName(it)) }
    if (isFrontend) add(FrontendIDEDataPaths.FRONTEND_DIR_NAME)
  }

  /**
   * The CI-safe path the artifacts of this launch are published under. Unlike the reporting directories, which live under the test's own
   * directory already, a CI artifact path is rooted at the build, so it has to spell the test out itself.
   */
  val artifactPath: String = buildList {
    if (!testNameSpellsTheMethodOut) add(ReportingPathUtils.testDirectoryName(testName))
    addAll(reportingDirSegments)
  }.filter(String::isNotEmpty).joinToString("/").replaceSpecialCharactersWithHyphens()

  /** What a test report calls this launch: the identity as it was given, neither hyphenated nor bounded. */
  val humanReadableTestName: String = buildList {
    if (testMethod == null) add(testName) else addAll(testMethod.nameSegments)
    launchName?.let(::add)
  }.filter(String::isNotEmpty).joinToString("/")

  // endregion

  // region Reporting directories

  private val launchReportingDir: Path = reportingDirSegments.fold(reportingRoot) { dir, segment -> dir.resolve(segment) }

  val reportsDir: Path = createReportingDirectory("reports")
  val snapshotsDir: Path = createReportingDirectory("snapshots")
  val logsDir: Path = createReportingDirectory("log")

  /** Where the JVM crash logs of this launch are copied, if it left any. Created on demand, unlike its siblings. */
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
    testContext.publishArtifact(
      source = source,
      artifactPath = artifactPath,
      // the path already names the launch, down to which half of a split-mode pair it is, so the file name only has to stay unique in time
      artifactName = ReportingPathUtils.formatArtifactName(artifactName),
    )
  }

  // endregion

  // region IDE diagnostics

  /**
   * Which IDE error reports under [logsDir] this launch answers for: `null`, the initial state, means all of them, and a set means only
   * those, empty included. `BackgroundRun.forceKill` snapshots the reports written so far through
   * [restrictIdeErrorReportsToExistingFiles], so that what the IDE logs while being killed is not blamed on the test.
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
