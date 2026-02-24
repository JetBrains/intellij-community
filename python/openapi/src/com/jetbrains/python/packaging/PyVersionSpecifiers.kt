// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Originally PoetryPythonVersion, PoetryVersionValue, and VersionType from com.jetbrains.python.poetry.PoetryFilesUtils
package com.jetbrains.python.packaging

import com.jetbrains.python.PyCommunityBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A parsed Python version specifier string (e.g., `>=3.8,<4.0`).
 *
 * Supports PEP 440 version specifiers (`==`, `!=`, `~=`, `<`, `<=`, `>=`, `>`),
 * wildcard matching (`==3.8.*`, `!=3.8.*`), and Poetry-style operators (`^`, `~`).
 * Use [isValid] to check whether a given Python version satisfies this specifier.
 *
 * Versions outside [LanguageLevel.SUPPORTED_LEVELS] are always rejected by [isValid],
 * regardless of the constraint. Use [ANY_SUPPORTED] when no constraint is needed.
 *
 * Only numeric release segments are supported (e.g., `3.10.2`).
 * PEP 440 pre-release (`3.8a1`), post-release (`.post1`), dev (`.dev1`),
 * epoch (`1!`), and local (`+local`) suffixes are not handled.
 *
 * @param constraintSpec comma-separated version specifier string (e.g., `>=3.8,<4.0`)
 */
@Internal
data class PyVersionSpecifiers(val constraintSpec: String) {
  private val conditions: List<Pair<VersionConstraintOperator, PythonVersionValue>> =
    constraintSpec.split(",").flatMap { parseSingleSpecifier(it.trim()) }

  fun isValid(versionString: String?): Boolean {
    if (versionString.isNullOrBlank()) return false
    val languageLevel = LanguageLevel.fromPythonVersionSafe(versionString) ?: return false
    if (languageLevel !in LanguageLevel.SUPPORTED_LEVELS) return false
    val version = PythonVersionValue.parse(versionString).successOrNull ?: return false
    return conditions.all { (operator, constraint) ->
      operator.isSatisfiedBy(version.compareTo(constraint, operator))
    }
  }

  fun isValid(languageLevel: LanguageLevel): Boolean {
    if (languageLevel !in LanguageLevel.SUPPORTED_LEVELS) return false
    return isValid(languageLevel.toString())
  }

  companion object {
    /** Matches any Python version from [LanguageLevel.SUPPORTED_LEVELS]. */
    val ANY_SUPPORTED: PyVersionSpecifiers = PyVersionSpecifiers("")

    fun parseSingleSpecifier(spec: String): List<Pair<VersionConstraintOperator, PythonVersionValue>> {
      if (spec.isEmpty()) return emptyList()
      val firstDigit = spec.indexOfFirst { it.isDigit() }
      if (firstDigit == -1) return emptyList()
      val operatorStr = spec.substring(0, firstDigit).trim()
      val version = PythonVersionValue.parse(spec.substring(firstDigit).trim().removeSuffix(".*")).successOrNull ?: return emptyList()
      return when (operatorStr) {
        "~=" -> expandCompatibleRelease(version)
        "~" -> expandTilde(version)
        "^" -> expandCaret(version)
        else -> {
          val operator = VersionConstraintOperator.parse(operatorStr) ?: return emptyList()
          listOf(operator to version)
        }
      }
    }

    /** PEP 440 compatible release: `~=3.8` → `>=3.8, <4.0`; `~=3.8.5` → `>=3.8.5, <3.9.0` */
    fun expandCompatibleRelease(version: PythonVersionValue): List<Pair<VersionConstraintOperator, PythonVersionValue>> {
      val upper = if (version.patch != null) {
        PythonVersionValue(version.major, (version.minor ?: 0) + 1, 0)
      }
      else {
        PythonVersionValue(version.major + 1, 0, null)
      }
      return listOf(
        VersionConstraintOperator.MORE_OR_EQUAL to version,
        VersionConstraintOperator.LESS to upper,
      )
    }

    /** Poetry tilde: `~3.8` → `>=3.8, <3.9`; `~3.8.5` → `>=3.8.5, <3.9.0` */
    fun expandTilde(version: PythonVersionValue): List<Pair<VersionConstraintOperator, PythonVersionValue>> {
      val minor = version.minor
      val upper = if (minor != null) {
        PythonVersionValue(version.major, minor + 1, 0)
      }
      else {
        PythonVersionValue(version.major + 1, 0, null)
      }
      return listOf(
        VersionConstraintOperator.MORE_OR_EQUAL to version,
        VersionConstraintOperator.LESS to upper,
      )
    }

    /** Poetry caret: `^3.8` → `>=3.8, <4.0`; `^0.8` → `>=0.8, <0.9` */
    fun expandCaret(version: PythonVersionValue): List<Pair<VersionConstraintOperator, PythonVersionValue>> {
      val minor = version.minor
      val patch = version.patch
      val upper = when {
        version.major != 0 -> PythonVersionValue(version.major + 1, 0, 0)
        minor != null && minor != 0 -> PythonVersionValue(0, minor + 1, 0)
        patch != null -> PythonVersionValue(0, 0, patch + 1)
        minor != null -> PythonVersionValue(0, minor + 1, 0)
        else -> PythonVersionValue(version.major + 1, 0, null)
      }
      return listOf(
        VersionConstraintOperator.MORE_OR_EQUAL to version,
        VersionConstraintOperator.LESS to upper,
      )
    }
  }
}

/**
 * A parsed Python version with [major], optional [minor], and optional [patch] components (e.g., `3.10.2`).
 */
@JvmInline
@Internal
value class PythonVersionValue private constructor(private val version: Triple<Int, Int?, Int?>) : Comparable<PythonVersionValue> {
  val major: Int get() = version.first
  val minor: Int? get() = version.second
  val patch: Int? get() = version.third

  internal constructor(major: Int, minor: Int?, patch: Int?) : this(Triple(major, minor, patch))

  /**
   * Compares this version to [other] in the context of the given [operator].
   * Missing constraint components are filled based on operator semantics:
   * - `<` and `>=`: missing defaults to 0 (e.g., `>=3.8` means `>=3.8.0`)
   * - `<=` and `>`: missing defaults to a high value (treating as "any subversion")
   * - `==` and `!=`: missing matches any value
   */
  fun compareTo(other: PythonVersionValue, operator: VersionConstraintOperator): Int {
    val default = when (operator) {
      VersionConstraintOperator.LESS, VersionConstraintOperator.MORE_OR_EQUAL -> 0
      VersionConstraintOperator.LESS_OR_EQUAL, VersionConstraintOperator.MORE -> 20
      VersionConstraintOperator.EQUAL, VersionConstraintOperator.NOT_EQUAL -> null
    }
    return major.compareTo(other.major).takeIf { it != 0 }
           ?: minor?.compareTo(other.minor ?: (default ?: minor ?: 0))?.takeIf { it != 0 }
           ?: patch?.compareTo(other.patch ?: (default ?: patch ?: 0))?.takeIf { it != 0 }
           ?: 0
  }

  override fun compareTo(other: PythonVersionValue): Int =
    major.compareTo(other.major).takeIf { it != 0 }
    ?: (minor ?: 0).compareTo(other.minor ?: 0).takeIf { it != 0 }
    ?: (patch ?: 0).compareTo(other.patch ?: 0).takeIf { it != 0 }
    ?: 0

  companion object {
    /**
     * Parses a dotted version string (e.g., `3`, `3.10`, `3.10.2`).
     */
    fun parse(versionString: String): PyResult<PythonVersionValue> {
      val parts = try {
        versionString.split(".").map { it.toInt() }
      }
      catch (_: NumberFormatException) {
        return PyResult.localizedError(PyCommunityBundle.message("python.version.invalid", versionString))
      }
      return when (parts.size) {
        1 -> Result.success(PythonVersionValue(parts[0], null, null))
        2 -> Result.success(PythonVersionValue(parts[0], parts[1], null))
        3 -> Result.success(PythonVersionValue(parts[0], parts[1], parts[2]))
        else -> PyResult.localizedError(PyCommunityBundle.message("python.version.invalid", versionString))
      }
    }
  }
}

/**
 * Comparison operators used in Python version specifiers (e.g., `>=3.8,<4.0`).
 */
@Internal
enum class VersionConstraintOperator {
  LESS,
  LESS_OR_EQUAL,
  EQUAL,
  NOT_EQUAL,
  MORE_OR_EQUAL,
  MORE;

  fun isSatisfiedBy(comparisonResult: Int): Boolean = when (this) {
    LESS -> comparisonResult < 0
    LESS_OR_EQUAL -> comparisonResult <= 0
    EQUAL -> comparisonResult == 0
    NOT_EQUAL -> comparisonResult != 0
    MORE_OR_EQUAL -> comparisonResult >= 0
    MORE -> comparisonResult > 0
  }

  companion object {
    fun parse(symbol: String): VersionConstraintOperator? = when (symbol) {
      "<" -> LESS
      "<=" -> LESS_OR_EQUAL
      "=", "==", "" -> EQUAL
      "!=" -> NOT_EQUAL
      ">=" -> MORE_OR_EQUAL
      ">" -> MORE
      else -> null
    }
  }
}
