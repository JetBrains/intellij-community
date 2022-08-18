package com.intellij.ide.starter.utils

import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.name


/**
 * Format: testMethodName => test-method-name
 */
fun String.hyphenateTestName(): String {

  fun hyphenateString(input: String) = input
    .replace(Regex("( )+"), "-")
    .replace(" ", "-").trim()
    .replaceFirstChar { it.lowercase(Locale.getDefault()) }.toCharArray()
    .map {
      if (it.isUpperCase()) "-${it.lowercaseChar()}"
      else it
    }
    .joinToString(separator = "")

  val hyphenatedPath = try {
    val originalPath = Path(this)

    var convertedPath = Path("")
    (0 until originalPath.nameCount).map { pathNameIndex ->
      convertedPath = convertedPath.resolve(hyphenateString(originalPath.getName(pathNameIndex).name))
    }

    convertedPath.toString()
  }
  catch (_: Exception) {
    return hyphenateString(this)
  }

  return hyphenatedPath
}