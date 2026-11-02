// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import kotlin.math.max

/**
 * The steps of an incremental test case, in the format of the Kotlin repository.
 *
 * A step is a set of `<file>.new`, `<file>.touch` and `<file>.delete` files. The numbered form `<file>.new.<n>` gives up
 * to ten steps. A case uses one form only. In a multi-module case, the `<module>_` prefix of a file name selects the
 * module.
 */
internal object KotlinJpsModifications {
  private val COMMANDS = listOf("new", "touch", "delete")
  private val UNNUMBERED = Regex(".+\\.(${COMMANDS.joinToString("|")})$")
  private val NUMBERED = Regex(".+\\.(${COMMANDS.joinToString("|")})\\.\\d+$")

  /** Copies the `.kt` and `.java` files of [testDataDir] with [filePrefix] into [destination], without the prefix. */
  fun copyTestSources(testDataDir: File, destination: File, filePrefix: String) {
    fun copyDir(fromDir: File, toDir: File) {
      FileUtil.ensureExists(toDir)
      for (file in fromDir.listFiles().orEmpty()) {
        if (file.isDirectory) {
          copyDir(file, File(toDir, file.name))
        }
        else if ((file.name.endsWith(".kt") || file.name.endsWith(".java")) && file.name.startsWith(filePrefix)) {
          FileUtil.copy(file, File(toDir, file.name.substring(filePrefix.length)))
        }
      }
    }
    copyDir(testDataDir, destination)
  }

  /**
   * @param moduleNames the modules of a multi-module case, or `null` for a single module case.
   * @return the steps, each a list of modifications.
   */
  fun stepsOf(testDataDir: File, moduleNames: Collection<String>?): List<List<Modification>> {
    val files = testDataDir.walkTopDown().filter { it.isFile }.toList()
    val hasUnnumbered = files.any { it.name.matches(UNNUMBERED) }
    val hasNumbered = files.any { it.name.matches(NUMBERED) }
    check(!(hasUnnumbered && hasNumbered)) { "Bad test data format: both unnumbered and numbered modifications in $testDataDir" }
    if (!hasUnnumbered && !hasNumbered) return emptyList()

    if (hasUnnumbered) return listOf(stepOf(testDataDir, files, moduleNames, ""))
    return (1..10).map { stepOf(testDataDir, files, moduleNames, ".$it") }.filter { it.isNotEmpty() }
  }

  private fun stepOf(testDataDir: File, files: List<File>, moduleNames: Collection<String>?, stepSuffix: String): List<Modification> {
    val result = LinkedHashMap<String, Modification>()
    for (file in files) {
      val command = COMMANDS.firstOrNull { file.path.endsWith(".$it$stepSuffix") } ?: continue
      val suffix = ".$command$stepSuffix"
      val (moduleName, fileName) = splitModulePrefix(file.toRelativeString(testDataDir), moduleNames)
      val srcDir = moduleName?.let { "$it/src" } ?: "src"
      val targetPath = srcDir + "/" + FileUtil.toSystemIndependentName(fileName.removeSuffix(suffix))
      result.putIfAbsent(targetPath, when (command) {
        "new" -> Modification.ModifyContent(targetPath, file)
        "touch" -> Modification.Touch(targetPath)
        else -> Modification.Delete(targetPath)
      })
    }
    return result.values.toList()
  }

  private fun splitModulePrefix(fileName: String, moduleNames: Collection<String>?): Pair<String?, String> {
    val underscore = fileName.indexOf('_')
    if (underscore == -1) {
      check(moduleNames == null) { "The case is multi-module, but $fileName has no module prefix" }
      return null to fileName
    }

    var moduleName = fileName.substring(0, underscore)
    var moduleFileName = fileName.substring(underscore + 1)
    if (moduleName.all { it.isDigit() }) {
      moduleName = moduleFileName.substringBefore('_')
      moduleFileName = moduleFileName.substringAfter('_')
    }
    if (moduleNames == null) return null to fileName
    check(moduleName in moduleNames) { "No module for the prefix of $fileName" }
    return moduleName to moduleFileName
  }

  sealed class Modification(val path: String) {
    abstract fun perform(workDir: File)

    override fun toString(): String = "${this::class.java.simpleName} $path"

    class ModifyContent(path: String, private val dataFile: File) : Modification(path) {
      override fun perform(workDir: File) {
        val file = File(workDir, path)
        val oldLastModified = file.lastModified()
        file.delete()
        dataFile.copyTo(file)
        // Some file systems keep the timestamp in seconds
        if (file.lastModified() <= oldLastModified) {
          file.setLastModified(oldLastModified + 1000)
        }
      }
    }

    class Touch(path: String) : Modification(path) {
      override fun perform(workDir: File) {
        val file = File(workDir, path)
        // Some file systems keep the timestamp in seconds
        file.setLastModified(max(System.currentTimeMillis(), file.lastModified() + 1000))
      }
    }

    class Delete(path: String) : Modification(path) {
      override fun perform(workDir: File) {
        val file = File(workDir, path)
        check(file.delete()) { "Cannot delete $file" }
      }
    }
  }
}
