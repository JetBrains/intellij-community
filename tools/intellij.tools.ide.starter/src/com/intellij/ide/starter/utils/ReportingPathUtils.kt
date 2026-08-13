package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.util.common.logError
import com.intellij.util.io.DigestUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SHORTENED_NAME_HASH_LENGTH = 6
private const val MAX_FILE_NAME_LENGTH_IN_BYTES = 255
private const val MAX_DIR_NAME_LENGTH_IN_BYTES = 50
private const val TEAMCITY_ARTIFACT_SUFFIX = "-2147483647.zip"

/**
 * Shared path-length rules and name-shortening utilities for paths created by IDE Starter.
 *
 * Limits are exclusive. Child file names are shortened against their actual absolute directory so that the complete path stays below
 * [PATH_LENGTH_LIMIT].
 *
 * Shortening happens the same way everywhere, so that a path names the same thing on every OS; only failing over a path that came out too
 * long anyway is conditional, since [PATH_LENGTH_LIMIT] is not every OS's limit — see [isPathLengthLimitEnforced].
 */
@ApiStatus.Internal
object ReportingPathUtils {
  const val PATH_LENGTH_LIMIT: Int = 260

  /**
   * Whether a path over [PATH_LENGTH_LIMIT] is reported here, rather than left to the one OS its length bothers.
   *
   * The limit is Windows' own
   * ([maximum path length](https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation)), so it is checked where
   * Windows is there to hit it. Linux and macOS take paths several times longer and the agents make use of that: a Bazel test runs out of a
   * runfiles tree under an output base that is only kept short on Windows, and on those two it is over 200 characters deep before Starter
   * has added anything of its own. Enforcing the limit there would fail a run over a path that works perfectly well where it was built.
   *
   * Checked outside CI on every OS all the same, because a name too long for Windows is written on whatever machine its author has, and
   * that is the last place it is cheap to fix. The alternative is hearing about it from a Windows build.
   */
  private val isPathLengthLimitEnforced: Boolean
    get() = SystemInfoRt.isWindows || !CIServer.instance.isBuildRunningOnCI

  /**
   * Reports a path that does not fit within [PATH_LENGTH_LIMIT] as a test infrastructure failure, where the limit is enforced at all — see
   * [isPathLengthLimitEnforced].
   *
   * [path] is returned as it is, and whatever was about to be done with it is done anyway: the only fix is a shorter name somewhere above,
   * so naming which path to shorten is all this can usefully do, and refusing the path on top of that would take a run's reports away over
   * a length that the OS it runs on is perfectly happy with.
   */
  fun checkPathLength(path: Path): Path {
    if (!isPathLengthLimitEnforced) return path

    val absolutePath = path.toAbsolutePath().normalize()
    val length = absolutePath.toString().length
    if (length < PATH_LENGTH_LIMIT) return path

    val message = "Path '$absolutePath' is $length characters long, which exceeds the $PATH_LENGTH_LIMIT-character limit."
    // the CI server of a local run has nowhere to report to, and its own message says nothing about the path
    logError(message)
    CIServer.instance.reportTestFailure(
      testName = "Path exceeds $PATH_LENGTH_LIMIT characters: $absolutePath",
      message = message,
      details = "Long paths fail on Windows (https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation)",
      kind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
    )
    return path
  }

  /**
   * The file name a published artifact takes: `<type>-<timestamp>`, timestamped so that several artifacts of one type can land in one
   * directory, and short enough for the suffix TeamCity appends. [testName] qualifies it for whoever publishes without a directory of their
   * own to tell the tests apart.
   */
  fun formatArtifactName(artifactType: String, testName: String = ""): String {
    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    val name = listOf(artifactType, testName.replace("/", "-").replace(" ", ""), time)
      .filter(String::isNotEmpty)
      .joinToString("-")
    return shortenWithHashIfNeeded(name, MAX_FILE_NAME_LENGTH_IN_BYTES - TEAMCITY_ARTIFACT_SUFFIX.length)
  }

  /**
   * One reporting directory name, bounded so that a path built out of such names stays within [PATH_LENGTH_LIMIT]. [prefix] is kept whole
   * and [name] gets whatever the limit leaves.
   */
  fun dirName(name: String, prefix: String = ""): String =
    prefix + shortenWithHashIfNeeded(name, MAX_DIR_NAME_LENGTH_IN_BYTES - prefix.length)

  /** Flattens a test name into one bounded directory segment. */
  fun testDirectoryName(testName: String): String = dirName(testName.replace('/', '-'))

  /** Returns [name] unchanged when it fits; otherwise shortens it and appends a stable hash of the full name. */
  fun shortenWithHashIfNeeded(name: String, maxLengthInBytes: Int): String {
    require(maxLengthInBytes > SHORTENED_NAME_HASH_LENGTH) {
      "Maximum length must leave room for the hash suffix"
    }

    val bytes = name.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxLengthInBytes) return name

    val hash = DigestUtil.sha256Hex(bytes).take(SHORTENED_NAME_HASH_LENGTH)
    // Reporting names are expected to be ASCII; non-ASCII truncation may be imprecise
    val prefix = name.take(maxLengthInBytes - SHORTENED_NAME_HASH_LENGTH - 1)
    return "$prefix-$hash"
  }
}
