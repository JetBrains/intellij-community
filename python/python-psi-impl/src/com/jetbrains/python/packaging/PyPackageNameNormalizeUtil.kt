package com.jetbrains.python.packaging


private val QUOTES_REGEX: Regex = Regex("^\"|\"$")
private val SEPARATOR_REGEX: Regex = Regex("[-_.]+")

/**
 * Normalizes a package name by removing quotes, replacing separators, and converting to lowercase.
 */
fun normalizePackageName(name: String): String = name
  .replace(QUOTES_REGEX, "")
  .replace(SEPARATOR_REGEX, "-")
  .lowercase()