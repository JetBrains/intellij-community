// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.packageRequirements.TreeParser
import com.jetbrains.python.venvReader.tryResolvePath
import java.nio.file.Path
import kotlin.io.path.exists

object UvOutputParser {
  private val WHITESPACE_REGEX = Regex("\\s+")
  private const val REQUIRES_LINE_PREFIX = "Requires:"

  fun parseUvPackageList(input: String): List<PythonPackage> {
    val packageList = mutableListOf<PythonPackage>()
    for (line in input.lines().drop(1)) {
      if (line.isBlank()) continue
      if (TreeParser.isRootLine(line)) break
      val parts = line.trim().split(WHITESPACE_REGEX).drop(1)
      if (parts.isEmpty()) continue
      val packageName = parts[0]
      val version = parts.getOrElse(1) { "" }.removePrefix("v")
      packageList.add(PythonPackage(packageName, version, false))
    }
    return packageList
  }

  fun parseUvPythonList(uvDir: Path, out: String): Set<Path> {
    val lines = out.lines()
    val pythons = lines.mapNotNull { line ->
      val arrow = line.lastIndexOf("->").takeIf { it > 0 } ?: line.length

      val pythonAndPath = line
        .substring(0, arrow)
        .trim()
        .split(delimiters = arrayOf(" ", "\t"), limit = 2)

      if (pythonAndPath.size != 2) {
        return@mapNotNull null
      }

      val python = tryResolvePath(pythonAndPath[1].trim())
        ?.takeIf { it.exists() && it.startsWith(uvDir) }

      python
    }.toSet()

    return pythons
  }

  fun parseUvPackageRequirements(input: String): List<PyPackageName> {
    val requiresLine = input.lines().find { it.startsWith(REQUIRES_LINE_PREFIX) } ?: return emptyList()

    return requiresLine
      .removePrefix(REQUIRES_LINE_PREFIX)
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { PyPackageName.from(it) }
  }
}
