package com.jetbrains.python.packaging


fun normalizePackageName(packageName: String): String {
  var name = packageName.trim()
    .removePrefix("\"")
    .removeSuffix("\"")

  // e.g. __future__
  if (!name.startsWith("_")) {
    name = name.replace('_', '-')
  }

  return name
    .replace('.', '-')
    .lowercase()
}