// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This script was implemented to sync local copy of `typeshed` with bundled `typeshed`.
 *
 * As a result it leaves top-level modules and packages that are listed in `whiteList`.
 * It allows us to reduce the size of bundled `typeshed` and do not run indexing and other analyzing processes on disabled stubs.
 */

val repo = Paths.get("../../../../../../../../../typeshed").abs().normalize()
val bundled = Paths.get("../../../../../../../../community/python/helpers/typeshed").abs().normalize()

println("Repo: ${repo.abs()}")
println("Bundled: ${bundled.abs()}")

sync(repo, bundled)

val whiteList = setOf("typing", "six", "__builtin__", "builtins", "exceptions", "types", "datetime", "functools", "shutil", "re", "time",
                      "argparse", "uuid", "threading", "signal", "collections", "subprocess", "math", "queue", "socket", "sqlite3", "attr",
                      "pathlib", "io", "itertools", "ssl")

clean(topLevelPackages(bundled), whiteList)

fun sync(repo: Path, bundled: Path) {
  if (!Files.exists(repo)) throw IllegalArgumentException("Not found: ${repo.abs()}")

  if (Files.exists(bundled)) {
    bundled.deleteRecursively()
    println("Removed: ${bundled.abs()}")
  }

  val whiteList = setOf("stdlib",
                        "tests",
                        "third_party",
                        ".flake8",
                        ".gitignore",
                        ".travis.yml",
                        "CONTRIBUTING.md",
                        "README.md",
                        "LICENSE",
                        "requirements-tests-py2.txt",
                        "requirements-tests-py3.txt")

  Files
    .newDirectoryStream(repo)
    .forEach {
      if (it.name() in whiteList) {
        val target = bundled.resolve(it.fileName)

        it.copyRecursively(target)
        println("Copied: ${it.abs()} to ${target.abs()}")
      }
      else {
        println("Skipped: ${it.abs()}")
      }
    }
}

fun topLevelPackages(typeshed: Path): List<Path> {
  return sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("third_party")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .toList()
}

fun clean(topLevelPackages: List<Path>, whiteList: Set<String>) {
  topLevelPackages
    .asSequence()
    .filter { it.nameWithoutExtension() !in whiteList }
    .forEach { it.deleteRecursively() }
}

fun Path.abs() = toAbsolutePath()
fun Path.deleteRecursively() = toFile().deleteRecursively()
fun Path.copyRecursively(target: Path) = toFile().copyRecursively(target.toFile())
fun Path.name() = toFile().name
fun Path.nameWithoutExtension() = toFile().nameWithoutExtension
