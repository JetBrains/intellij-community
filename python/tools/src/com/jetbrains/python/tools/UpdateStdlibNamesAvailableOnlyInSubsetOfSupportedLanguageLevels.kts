// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.util.text.nullize
import com.jetbrains.python.psi.LanguageLevel
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val typeshedRepoPath: Path = Paths.get("../../../../../../../../../typeshed").abs().normalize()
val bundledHelpersPath: Path = Paths.get("../../../../../../../../community/python/helpers/typeshed").abs().normalize()

val blacklistedPackages = sequenceOf(
  "google-cloud-ndb",
  "optparse", // deprecated
  "protobuf",
  "xxlimited", // not available in runtime
).mapTo(hashSetOf()) { it.lowercase() }

println("Processing stdlib/VERSIONS")
printPythonPackageVersions(bundledHelpersPath, blacklistedPackages)

// Transforms version string from '3.x-3.y' to 'LanguageLevel.PYTHON3X to LanguageLevel.PYTHON3Y'
fun toLanguageLevel(version: String): String {
  val parts = version.split('-')
  val startPart = if (parts[0].isEmpty()) "null" else "LanguageLevel.PYTHON" + parts[0].replace(".", "")
  val endPart = if (parts.size < 2 || parts[1].trim().isEmpty()) "null" else "LanguageLevel.PYTHON" + parts[1].replace(".", "")
  return "($startPart to $endPart)"
}

// Transforms package line from 'package, version' to '"package" to versionInLanguageLevel'
fun toPackageLine(line: String): String {
  val parts = line.split(", ")
  val packageName = parts[0]
  val version = parts[1]
  val languageLevel = toLanguageLevel(version)
  return "\"$packageName\" to $languageLevel"
}

fun getTimeString(): String {
  val currentDateTime = Instant.now().atOffset(ZoneOffset.UTC)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  return currentDateTime.format(formatter)
}

// Unites two lists to have entries from both
fun unitePackageLists(oldList: List<String>, newList: List<String>): List<String> {
  val indentation = " ".repeat(oldList[0].indexOf("\""))
  val oldPackagesMap = oldList.toPackageMap()
  val newPackagesMap = newList.toPackageMap()

  val result = HashMap<String, String>()
  newPackagesMap.forEach { pkgName, pkgVersion ->
    if (pkgName !in oldPackagesMap) {
      result[pkgName] = "${pkgVersion},"
    } else {
      result[pkgName] = oldPackagesMap[pkgName]!!
    }
  }

  return result.map { "$indentation\"${it.key}\" to ${it.value}" }.sorted()
}

// Helper function to split package line to a map
fun List<String>.toPackageMap() = map {
  val parts = it.split("\" to ")
  parts[0].substringAfter("\"") to parts[1]
}.toMap()

fun printPythonPackageVersions(repo: Path, blackList: Set<String>) {
  val earliestPython3Version = LanguageLevel.SUPPORTED_LEVELS.filter { it.isPy3K }.minOrNull()!!
  val latestPythonVersion = LanguageLevel.SUPPORTED_LEVELS.maxOrNull()!!

  val packageLines = Files
    .readAllLines(repo.resolve("stdlib/VERSIONS"))
    .asSequence()
    .map { it.substringBefore('#') }
    .filterNot { it.isBlank() }
    .map { it.split(": ", limit = 2) }
    .filter { it.size == 2 }
    .filter { it[0] !in blackList }
    .filter {
      val bounds = it[1]
      val lowerBound = LanguageLevel.fromPythonVersion(bounds.substringBefore('-'))!!
      val upperBound = LanguageLevel.fromPythonVersion(bounds.substringAfter('-').nullize(true)) ?: latestPythonVersion
      earliestPython3Version.isOlderThan(lowerBound) || upperBound.isOlderThan(latestPythonVersion)
    }
    .map { "${it[0]}, ${it[1]}" }
    .toList()

  packageLines.forEach(::println)

  val transformedLines = packageLines.map(::toPackageLine)

  val pathToPyTypeShed = "../../../../../../../../community/python/python-psi-impl/src/com/jetbrains/python/codeInsight/typing/PyTypeShed.kt"

  val fileLines = File(pathToPyTypeShed).readLines()
  val mapStartIndex = fileLines.indexOfFirst { it.endsWith("// name to python versions when this name was introduced and removed") } + 1
  val mapEndIndex = fileLines.subList(mapStartIndex, fileLines.lastIndex).indexOfFirst { it.trim().startsWith(")") }

  val newPackageList = unitePackageLists(
    fileLines.subList(mapStartIndex, mapStartIndex + mapEndIndex),
    transformedLines
  )

  val updatedFileLines = fileLines.subList(0, mapStartIndex) +
                         newPackageList +
                         "  ) // Modified by script ${getTimeString()}" +
                         fileLines.subList(mapStartIndex + mapEndIndex + 1, fileLines.size)

  File(pathToPyTypeShed).writeText(updatedFileLines.joinToString("\n"))
}

fun Path.abs() = toAbsolutePath()