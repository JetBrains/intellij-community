package com.intellij.ide.starter.utils

import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.name


/**
 * Format: testMethodName => test-method-name
 */
fun String.hyphenateTestName(): String {

  fun hyphenateString(input: String) = input
    .replace(Regex("( )+"), "-")
    .replace(" ", "-").trim()
    .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
    .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
    .lowercase(Locale.getDefault())

  val hyphenatedPath = try {
    val originalPath = Path(this)

    var convertedPath = Path("")
    (0 until originalPath.nameCount).forEach { pathNameIndex ->
      convertedPath = convertedPath.resolve(hyphenateString(originalPath.getName(pathNameIndex).name))
    }

    convertedPath.toString().replace(convertedPath.fileSystem.separator, "/")
  }
  catch (_: Exception) {
    return hyphenateString(this).replaceSpecialCharactersWithHyphens().replace("-*/-*".toRegex(), "/")
  }

  return hyphenatedPath.replaceSpecialCharactersWithHyphens()
}

/** A name as a reporting directory spells it: hyphenated, and its slashes flattened, one name getting one directory. */
internal fun String.flattened(): String = hyphenateTestName().replace('/', '-')

/** `.` and `..` spelled so that a directory name cannot point at another directory. */
internal fun String.escapeDotSegment(): String = if (this == "." || this == "..") replace(".", "%2E") else this

/**
 * Whether this name begins with the whole of [name] rather than merely beginning like it: `maven-smoke-tests`, `maven-smoke-tests-x` and
 * `maven-smoke-tests.x` all begin with the whole of `maven-smoke-tests`, `maven-smoke-testsuite` does not.
 */
internal fun String.startsWithWholeName(name: String): Boolean =
  startsWith(name) && getOrNull(name.length)?.isLetterOrDigit() != true
