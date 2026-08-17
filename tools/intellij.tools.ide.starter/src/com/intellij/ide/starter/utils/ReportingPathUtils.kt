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

private const val MAX_FILE_NAME_LENGTH_IN_BYTES = 255
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
   * The length of the hash [shortenWithHashIfNeeded] appends to a name it had to cut down. Deliberately short: a path on Windows has 260
   * characters for everything, and two names cut down to the same prefix that also collide over 16 bits are rare enough to live with.
   */
  const val NAME_HASH_LENGTH: Int = 4

  /** The longest one reporting directory name gets, a hash of what was cut away included. */
  const val MAX_DIR_NAME_LENGTH_IN_BYTES: Int = 45

  /**
   * The longest the one directory of a launch gets. Far shorter than [MAX_DIR_NAME_LENGTH_IN_BYTES]: a launch name largely repeats what the
   * method above it is called, so little of it is worth a path's length. Nothing compares the two names — the bound is simply tighter.
   */
  const val MAX_LAUNCH_DIR_NAME_LENGTH_IN_BYTES: Int = 25

  /** The longest a published artifact name gets: what a file name has left once TeamCity has appended a suffix of its own. */
  const val MAX_ARTIFACT_NAME_LENGTH_IN_BYTES: Int = MAX_FILE_NAME_LENGTH_IN_BYTES - TEAMCITY_ARTIFACT_SUFFIX.length

  /** The longest name a JVM crash log gets: the JVM expands `%p` to a process id, 32 bits wide at most on every OS Starter runs on. */
  val WIDEST_CRASH_LOG_NAME: String = "java_error_in_idea_${UInt.MAX_VALUE}.log"

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
   * Reports [directory] unless it can still hold the crash log of any process. A directory has to be checked against the widest name it will
   * ever hold rather than against itself, because the JVM only expands `%p` once it has already crashed: a directory that fits
   * `-XX:ErrorFile` but not the file it names loses exactly the diagnostics the crash was supposed to leave behind.
   */
  fun checkCrashLogDirectoryLength(directory: Path): Path {
    checkPathLength(directory.resolve(WIDEST_CRASH_LOG_NAME))
    return directory
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
    return shortenWithHashIfNeeded(name, MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)
  }

  /**
   * One reporting directory name, bounded so that a path built out of such names stays within [PATH_LENGTH_LIMIT]. [prefix] is kept whole
   * and [name] gets whatever the limit leaves.
   */
  fun dirName(name: String, prefix: String = ""): String =
    prefix + shortenWithHashIfNeeded(name, MAX_DIR_NAME_LENGTH_IN_BYTES - prefix.length)

  /** Flattens a test name into one bounded directory segment. */
  fun testDirectoryName(testName: String): String = dirName(testName.replace('/', '-'))

  /**
   * The one directory a launch reports in: the last level [launchName] names, cut to [MAX_LAUNCH_DIR_NAME_LENGTH_IN_BYTES]. A name that
   * lost anything — a level above the last, or bytes over the bound — carries a hash of the whole of it instead, which is what tells one
   * launch of a method from another once the rest is gone. `null` when [launchName] names no level at all, being nothing but separators.
   */
  fun launchDirNameOf(launchName: String): String? {
    // the last level that names something: a name trailing off into separators has already been spelled out one level up
    val lastLevel = launchName.split('/').lastOrNull(String::isNotEmpty)?.escapeDotSegment() ?: return null
    return shortenWithHashIfNeeded(lastLevel, MAX_LAUNCH_DIR_NAME_LENGTH_IN_BYTES, hashedName = launchName)
  }

  /**
   * Returns [name] unchanged when it is the whole of what it stands for and fits; otherwise shortens it and appends a stable hash.
   *
   * [hashedName] is what that hash is taken of and defaults to [name]. Pass the longer name [name] is only a part of, and the result
   * carries the hash whether or not [name] itself fits: what was left out above is exactly what the hash is there to tell apart.
   */
  fun shortenWithHashIfNeeded(name: String, maxLengthInBytes: Int, hashedName: String = name): String {
    require(maxLengthInBytes > NAME_HASH_LENGTH) {
      "Maximum length must leave room for the hash suffix"
    }

    if (hashedName == name && name.toByteArray(Charsets.UTF_8).size <= maxLengthInBytes) return name

    // Reporting names are expected to be ASCII; non-ASCII truncation may be imprecise
    // a cut that lands on a hyphen leaves one, and the artifact path collapses hyphen runs, so keeping it spells the name two ways
    // a cut that lands on a separator would leave a directory named after nothing but the hash, the name itself having ended above it
    val prefix = name.take(maxLengthInBytes - NAME_HASH_LENGTH - 1).trimEnd('-', '/')
    return "$prefix-${nameHash(hashedName)}"
  }

  /**
   * A short stable hash of [name], for a directory name that keeps only a part of what it is named after: whatever was left out, the hash
   * still tells this name from every other one that was cut down the same way.
   */
  fun nameHash(name: String): String =
    DigestUtil.sha256Hex(name.toByteArray(Charsets.UTF_8)).take(NAME_HASH_LENGTH)
}
