// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
  val repo = Paths.get("../typeshed")
  val bundled = Paths.get("./community/python/helpers/typeshed")

  println("Repo: ${repo.abs()}")
  println("Bundled: ${bundled.abs()}")

  sync(repo, bundled)
  clean(topLevelPackages(bundled), PyTypeShed.WHITE_LIST)
}

private fun sync(repo: Path, bundled: Path) {
  if (!repo.exists()) throw IllegalArgumentException("Not found: ${repo.abs()}")

  if (bundled.exists()) {
    bundled.delete()
    println("Removed: ${bundled.abs()}")
  }

  bundled.createDirectories()
  if (!bundled.exists()) throw IllegalStateException("Not found: ${bundled.abs()}")

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

        it.toFile().copyRecursively(target.toFile())
        println("Copied: ${it.abs()} to ${target.abs()}")
      }
      else {
        println("Skipped: ${it.abs()}")
      }
    }
}

private fun topLevelPackages(typeshed: Path): List<Path> {
  return sequenceOf(typeshed)
    .flatMap { sequenceOf(it.resolve("stdlib"), it.resolve("third_party")) }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .flatMap { Files.newDirectoryStream(it).asSequence() }
    .toList()
}

private fun clean(topLevelPackages: List<Path>, whiteList: Set<String>) {
  topLevelPackages
    .asSequence()
    .filter { FileUtil.getNameWithoutExtension(it.name()) !in whiteList }
    .forEach { it.delete() }
}

private fun Path.abs() = toAbsolutePath()
private fun Path.name() = fileName.toString()