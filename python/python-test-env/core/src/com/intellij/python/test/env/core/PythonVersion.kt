// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import com.intellij.util.text.SemVer
import org.jetbrains.annotations.ApiStatus

/**
 * Major.minor to full major.minor.patch version mapping.
 * Generated dynamically from PyVersionMapping - uses the latest stable patch version for each major.minor.
 * Excludes pre-release versions (alpha, beta, rc, etc.).
 */
private val VERSION_TO_FULL: Map<String, SemVer> by lazy {
  PyVersionMapping.getPythonVersionMapping().keys
    .filter { it.preRelease == null }
    .groupBy { version ->
      "${version.major}.${version.minor}"
    }
    .mapValues { (_, versions) ->
      versions.maxOrNull()!!
    }
}

/**
 * Parse a Python version string into a SemVer instance.
 * 
 * Supports formats: "3.11", "3.12.5", "3.11.14", "3.10.1a1", "3.10.1b3", "3.10.1rc6", "3.13.2somestring"
 * 
 * If only major.minor is provided (e.g., "3.11"), automatically fills in the patch version
 * from the known version mapping. If the version is not in the mapping, defaults to patch 0.
 * If the full version is provided (e.g., "3.11.14"), uses it as-is.
 *
 * Pre-release suffixes (a, b, rc) with optional numbers are supported.
 * Arbitrary post-release strings (e.g., "3.13.2somestring") are also supported and sorted lowest.
 *
 * Python pre-release versions (a, b, rc) are parsed without the SemVer hyphen separator.
 * Pre-release versions are sorted: empty > rc > beta > alpha > other strings
 * Within the same pre-release type, higher numbers come first (e.g., rc6 > rc2, b10 > b3, a15 > a1)
 *
 * @param version Version string
 * @return Parsed SemVer with custom pre-release comparison
 * @throws IllegalArgumentException if the version string is invalid
 */
@ApiStatus.Internal
fun parsePythonVersion(version: String): SemVer {
  // Match pattern: major.minor[.patch][suffix]
  val regex = Regex("""^(\d+)\.(\d+)(?:\.(\d+)(.*))?$""")
  val match = regex.matchEntire(version)
    ?: throw IllegalArgumentException("Invalid Python version format: $version. Expected formats: '3.11', '3.12.5', '3.10.1a1', '3.10.1b3', '3.10.1rc6', '3.13.2somestring'")

  val major = match.groupValues[1].toInt()
  val minor = match.groupValues[2].toInt()
  val patchStr = match.groupValues[3]
  val suffix = match.groupValues[4].takeIf { it.isNotEmpty() }

  // If patch is provided, use it with the suffix
  if (patchStr.isNotEmpty()) {
    val patch = patchStr.toInt()
    return SemVer(version, major, minor, patch, suffix)
  }

  // Only major.minor provided, look up the full version (only for stable versions)
  if (suffix == null) {
    val majorMinor = "$major.$minor"
    val fullVersion = VERSION_TO_FULL[majorMinor]
    if (fullVersion != null) {
      return fullVersion
    }
    return SemVer("$major.$minor.0", major, minor, 0, null)
  }

  // major.minor with suffix, default patch to 0
  return SemVer("$major.$minor.0$suffix", major, minor, 0, suffix)
}

/**
 * Latest stable Python version.
 * Generated dynamically from PyVersionMapping - uses the highest major.minor.patch version available.
 * Excludes pre-release versions (alpha, beta, rc, etc.).
 */
val LATEST_PYTHON_VERSION: SemVer by lazy {
  PyVersionMapping.getPythonVersionMapping().keys
    .filter { it.preRelease == null }
    .maxOrNull()
    ?: error("No stable Python versions found in mapping")
}

/**
 * Extension function to check if a Python version matches a major.minor version string.
 * The patch version is ignored in the comparison.
 *
 * Examples:
 * - SemVer("3.11.14", 3, 11, 14).matches("3.11") -> true
 * - SemVer("3.11.14", 3, 11, 14).matches("3.12") -> false
 */
@ApiStatus.Internal
fun SemVer.matches(majorMinor: String): Boolean {
  val parts = majorMinor.split(".")
  if (parts.size < 2) return false
  return major == parts[0].toIntOrNull() && minor == parts[1].toIntOrNull()
}
