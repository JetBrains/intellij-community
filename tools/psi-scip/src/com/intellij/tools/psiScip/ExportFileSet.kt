// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class ExportFile(@JvmField val relativePath: String, @JvmField val file: VirtualFile, @JvmField val module: String)

/**
 * Selects the Java and Kotlin files to walk, sorted by relative path.
 *
 * The candidates are the `*.java` and `*.kt` files of `git ls-files`, or the lines of [filesFrom].
 * A candidate is walked when it is under a Java source root, of the production or the test type (a Kotlin source root is one).
 * A resource root, such as a `testData` directory, does not qualify.
 * The summary counts the other candidates as `resource_root`, `no_root`, `excluded` and `missing`.
 */
internal suspend fun selectFiles(project: Project, root: Path, filesFrom: Path?, summary: ExportSummary): List<ExportFile> {
  val candidates = (if (filesFrom == null) gitSourceFiles(root) else Files.readAllLines(filesFrom).map { it.trim() }.filter { it.isNotEmpty() })
    .filter { it.endsWith(".java") || it.endsWith(".kt") }
    .distinct()
    .sorted()
  val fileSystem = LocalFileSystem.getInstance()
  val result = ArrayList<ExportFile>(candidates.size)
  for (chunk in candidates.chunked(1000)) {
    readAction {
      val fileIndex = ProjectFileIndex.getInstance(project)
      for (path in chunk) {
        val file = fileSystem.findFileByNioFile(root.resolve(path))
        val category = when {
          file == null -> "missing"
          fileIndex.isExcluded(file) -> "excluded"
          fileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) -> {
            val module = ModuleUtilCore.findModuleForFile(file, project)?.name ?: "<no module>"
            result.add(ExportFile(path, file, module))
            null
          }
          fileIndex.isInSourceContent(file) -> "resource_root"
          else -> "no_root"
        }
        if (category != null) {
          summary.skippedFiles.merge(category, 1, Int::plus)
        }
      }
    }
  }
  return result
}

private fun gitSourceFiles(root: Path): List<String> {
  val process = ProcessBuilder("git", "-C", root.toString(), "ls-files", "-z", "--", "*.java", "*.kt")
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
  val output = process.inputStream.readAllBytes()
  check(process.waitFor(10, TimeUnit.MINUTES) && process.exitValue() == 0) { "git ls-files failed in $root" }
  return String(output, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
}
