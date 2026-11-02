// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import junit.framework.ComparisonFailure
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.zip.CRC32

/**
 * Compares two output directories, as the incremental tests of the Kotlin repository do.
 *
 * The listing has the checksum of each file. A file that differs is also shown as text, with the bytecode for a class.
 */
internal object KotlinJpsOutputComparison {
  private const val ROOT_PLACEHOLDER = "<DIR_ROOT_PLACEHOLDER>"

  /**
   * @param forgiveExtraFiles accept the extra files of [actual]. A failed build can leave other outputs than a rebuild.
   */
  fun assertEqualDirectories(expected: File, actual: File, forgiveExtraFiles: Boolean) {
    val expectedPaths = relativePaths(expected)
    val actualPaths = relativePaths(actual)
    val changedPaths = expectedPaths.intersect(actualPaths)
      .filter { !File(expected, it).readBytes().contentEquals(File(actual, it).readBytes()) }
      .sorted()

    val expectedString = directoryString(expected, changedPaths)
    val actualString = directoryString(actual, changedPaths)

    if (forgiveExtraFiles && changedPaths.isEmpty() && actualString.lines().containsAll(expectedString.lines())) return
    if (expectedString == actualString) return

    throw ComparisonFailure(
      "The output of the incremental build differs from the output of a rebuild",
      expectedString.replaceFirst(ROOT_PLACEHOLDER, expected.absolutePath),
      actualString.replaceFirst(ROOT_PLACEHOLDER, actual.absolutePath),
    )
  }

  private fun relativePaths(dir: File): Set<String> =
    dir.walkTopDown().filter { it.isFile }.map { FileUtil.toSystemIndependentName(it.relativeTo(dir).path) }.toSet()

  private fun directoryString(dir: File, interestingPaths: List<String>): String = buildString {
    appendLine(ROOT_PLACEHOLDER)
    fun addDirContent(directory: File, indent: String) {
      val children = directory.listFiles().orEmpty().filter { !it.name.startsWith(".") }.sortedWith(compareBy({ it.isDirectory }, { it.name }))
      for (child in children) {
        if (child.isDirectory) {
          if (child.list()?.isNotEmpty() == true) {
            appendLine("$indent${child.name}")
            addDirContent(child, "$indent  ")
          }
        }
        else {
          appendLine("$indent${child.name} ${checksum(child)}")
        }
      }
    }
    addDirContent(dir, "  ")

    for (path in interestingPaths) {
      appendLine("================ $path ================")
      appendLine(fileToString(File(dir, path)))
      appendLine()
    }
  }

  private fun checksum(file: File): String {
    val crc = CRC32()
    crc.update(file.readBytes())
    return java.lang.Long.toHexString(crc.value)
  }

  private fun fileToString(file: File): String {
    if (file.extension != "class") return file.readText()
    val out = StringWriter()
    ClassReader(file.readBytes()).accept(TraceClassVisitor(PrintWriter(out)), 0)
    return out.toString()
  }
}
