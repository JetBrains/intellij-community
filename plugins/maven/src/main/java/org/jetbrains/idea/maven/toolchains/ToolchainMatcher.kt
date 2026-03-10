// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.toolchains
import com.intellij.util.lang.JavaVersion

interface ToolchainMatcher {
  fun matches(key: String, value: String, model: ToolchainModel): Boolean

}

fun findMatcher(key: String): ToolchainMatcher {
  return when (key) {
    "version" -> ToolchainVersionMatcher
    "vendor" -> AlwaysMatcher
    else -> ExactMatcher
  }
}

object ToolchainVersionMatcher : ToolchainMatcher {
  override fun matches(key: String, value: String, model: ToolchainModel): Boolean {
    val actualVersion = model.provides[key] ?: return false

    return try {
      matchesVersionConstraint(value, actualVersion)
    } catch (e: Exception) {
      false
    }
  }

  private fun matchesVersionConstraint(constraint: String, actual: String): Boolean {
    var process = constraint
    val ranges = mutableListOf<VersionRange>()

    // Parse all ranges like [lower, upper) or (lower, upper]
    while (process.startsWith("[") || process.startsWith("(")) {
      val index1 = process.indexOf(')')
      val index2 = process.indexOf(']')

      var index = index2
      if (index2 < 0 || (index1 >= 0 && index1 < index2)) {
        index = index1
      }

      if (index < 0) {
        return false // Unbounded version range
      }

      val range = parseVersionRange(process.substring(0, index + 1)) ?: return false
      ranges.add(range)

      process = process.substring(index + 1).trim()

      if (process.startsWith(",")) {
        process = process.substring(1).trim()
      }
    }

    // If there's remaining text and we already have ranges, it's invalid
    if (process.isNotEmpty() && ranges.isNotEmpty()) {
      return false
    }

    val actualJavaVersion = JavaVersion.tryParse(actual) ?: return false

    // If no ranges, treat as exact version
    if (ranges.isEmpty()) {
      val constraintVersion = JavaVersion.tryParse(constraint) ?: return false
      return actualJavaVersion == constraintVersion
    }

    // Check if actual version matches any of the ranges
    return ranges.any { it.contains(actualJavaVersion) }
  }

  private fun parseVersionRange(rangeSpec: String): VersionRange? {
    if (rangeSpec.length < 3) return null

    val lowerInclusive = rangeSpec[0] == '['
    val upperInclusive = rangeSpec[rangeSpec.length - 1] == ']'

    val content = rangeSpec.substring(1, rangeSpec.length - 1)
    val parts = content.split(',', limit = 2)

    if (parts.size != 2) return null

    val lower = parts[0].trim()
    val upper = parts[1].trim()

    val lowerVersion = if (lower.isEmpty()) null else JavaVersion.tryParse(lower)
    val upperVersion = if (upper.isEmpty()) null else JavaVersion.tryParse(upper)

    return VersionRange(lowerVersion, lowerInclusive, upperVersion, upperInclusive)
  }

  private data class VersionRange(
    val lower: JavaVersion?,
    val lowerInclusive: Boolean,
    val upper: JavaVersion?,
    val upperInclusive: Boolean
  ) {
    fun contains(version: JavaVersion): Boolean {
      if (lower != null) {
        val cmp = version.compareTo(lower)
        if (lowerInclusive && cmp < 0) return false
        if (!lowerInclusive && cmp <= 0) return false
      }

      if (upper != null) {
        val cmp = version.compareTo(upper)
        if (upperInclusive && cmp > 0) return false
        if (!upperInclusive && cmp >= 0) return false
      }

      return true
    }
  }
}

object AlwaysMatcher : ToolchainMatcher {
  override fun matches(key: String, value: String, model: ToolchainModel): Boolean = true
}

object ExactMatcher : ToolchainMatcher {
  override fun matches(key: String, value: String, model: ToolchainModel): Boolean {
    return value == model.provides[key]
  }
}
