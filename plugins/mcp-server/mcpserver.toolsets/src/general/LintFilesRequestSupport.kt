package com.intellij.mcpserver.toolsets.general

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
enum class LintMinSeverity(
  @JvmField val apiValue: String,
  @JvmField val highlightSeverity: HighlightSeverity,
) {
  WARNING("warning", HighlightSeverity.WEAK_WARNING),
  ERROR("error", HighlightSeverity.ERROR);

  companion object {
    fun parse(value: String): LintMinSeverity {
      val normalized = value.trim().lowercase()
      return entries.firstOrNull { it.apiValue == normalized } ?: mcpFail("min_severity must be one of: warning, error")
    }

    fun fromErrorsOnly(errorsOnly: Boolean): LintMinSeverity = if (errorsOnly) ERROR else WARNING
  }
}

@Internal
data class RequestedLintFile(
  @JvmField val requestedPath: String,
  @JvmField val relativePath: String,
  @JvmField val resolvedPath: Path,
)

@Internal
fun prepareRequestedLintFiles(project: Project, files: List<String>): List<RequestedLintFile> {
  if (files.isEmpty()) {
    mcpFail("files must contain at least one path")
  }

  val projectDir = project.projectDirectory
  val requestedFiles = LinkedHashMap<String, RequestedLintFile>()
  for (rawPath in files) {
    val filePath = rawPath.trim().ifEmpty { mcpFail("files must not contain blank paths") }
    val resolvedPath = resolveExistingRegularFileInProject(pathInProject = filePath, projectDirectory = projectDir)

    val relativePath = projectDir.relativize(resolvedPath).toString()
    requestedFiles.putIfAbsent(relativePath, RequestedLintFile(filePath, relativePath, resolvedPath))
  }
  return requestedFiles.values.toList()
}
