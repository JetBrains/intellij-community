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
