package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@Internal
data class RequestedLintFile(
  @JvmField val requestedPath: String,
  @JvmField val relativePath: String,
  @JvmField val resolvedPath: Path,
)

@Internal
fun prepareRequestedLintFiles(project: Project, filePaths: List<String>): List<RequestedLintFile> {
  if (filePaths.isEmpty()) {
    mcpFail("file_paths must contain at least one path")
  }

  val projectDir = project.projectDirectory
  val requestedFiles = LinkedHashMap<String, RequestedLintFile>()
  for (rawPath in filePaths) {
    val filePath = rawPath.trim().ifEmpty { mcpFail("file_paths must not contain blank paths") }
    val resolvedPath = resolveInProject(filePath, projectDir)
    if (Files.notExists(resolvedPath)) {
      mcpFail("File not found: $filePath")
    }
    if (!resolvedPath.isRegularFile()) {
      mcpFail("Not a file: $filePath")
    }

    val relativePath = projectDir.relativize(resolvedPath).toString()
    requestedFiles.putIfAbsent(relativePath, RequestedLintFile(filePath, relativePath, resolvedPath))
  }
  return requestedFiles.values.toList()
}
