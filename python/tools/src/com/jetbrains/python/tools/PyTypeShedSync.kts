// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.util.io.delete
import com.intellij.util.text.nullize
import com.jetbrains.python.psi.LanguageLevel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This script was implemented to sync local copy of `typeshed` with bundled `typeshed`.
 *
 * As a result it skips top-level modules and packages that are listed in `blacklist`.
 * It allows us to reduce the size of bundled `typeshed` and do not run indexing and other analyzing processes on disabled stubs.
 */

val repo: Path = Paths.get("../../../../../../../../../typeshed").abs().normalize()
val bundled: Path = Paths.get("../../../../../../../../community/python/helpers/typeshed").abs().normalize()

println("Repo: ${repo.abs()}")
println("Bundled: ${bundled.abs()}")

println("Syncing")
sync(repo, bundled)

val blacklist = sequenceOf(
  "google-cloud-ndb",
  "optparse", // deprecated
  "protobuf",
  "xxlimited", // not available in runtime
).mapTo(hashSetOf()) { it.lowercase() }

println("Cleaning")
cleanTopLevelPackages(bundled, blacklist)

println("Processing stdlib/VERSIONS")
printStdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels(bundled, blacklist)

fun sync(repo: Path, bundled: Path) {
  if (!Files.exists(repo)) throw IllegalArgumentException("Not found: ${repo.abs()}")

  if (Files.exists(bundled)) {
    bundled.delete()
    println("Removed: ${bundled.abs()}")
  }

  val exclude = setOf(".git", ".idea")

  Files
    .newDirectoryStream(repo)
    .forEach {
      if (it.name() !in exclude) {
        val target = bundled.resolve(it.fileName)

        it.copyRecursively(target)
        println("Copied: ${it.abs()} to ${target.abs()}")
      }
      else {
        println("Skipped: ${it.abs()}")
      }
    }
}

fun cleanTopLevelPackages(typeshed: Path, blackList: Set<String>) {
  val whiteList = hashSetOf<String>()

  sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("stdlib/@python2"), it.resolve("stubs")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .filter {
      val name = it.nameWithoutExtension().lowercase()

      if (name in blackList) {
        true
      }
      else {
        whiteList.add(name)
        false
      }
    }
    .forEach { it.delete() }

  println("White list size: ${whiteList.size}")
  println("Black list size: ${blackList.size}")
}

fun printStdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels(repo: Path, blackList: Set<String>) {
  val lowestPython3 = LanguageLevel.SUPPORTED_LEVELS.filter { it.isPy3K }.minOrNull()!!
  val latestPython = LanguageLevel.SUPPORTED_LEVELS.maxOrNull()!!

  val lines = Files
    .readAllLines(repo.resolve("stdlib/VERSIONS"))
    .map { it.substringBefore('#') }
    .filterNot { it.isBlank() }
    .map { it.split(": ", limit = 2) }

  lines.filter { it.size == 2 }
    .filter { it.first() !in blackList }
    .filter {
      val bounds = it.last()
      val lowerBound = LanguageLevel.fromPythonVersion(bounds.substringBefore('-'))!!
      val upperBound = LanguageLevel.fromPythonVersion(bounds.substringAfter('-').nullize(true)) ?: latestPython
      lowestPython3.isOlderThan(lowerBound) || upperBound.isOlderThan(latestPython)
    }
    .forEach { println("${it.first()}, ${it.last()}") }

  lines.filter { it.size != 2 }.forEach { println("WARN: malformed line: ${it.first()}") }
}

fun Path.abs() = toAbsolutePath()
fun Path.copyRecursively(target: Path) = toFile().copyRecursively(target.toFile())
fun Path.name() = toFile().name
fun Path.nameWithoutExtension() = toFile().nameWithoutExtension
