package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal fun resolveExistingRegularFileInProject(project: Project, pathInProject: String): Path {
  val resolvedPath = project.resolveInProject(pathInProject)
  val attributes = try {
    Files.readAttributes(resolvedPath, BasicFileAttributes::class.java)
  }
  catch (_: NoSuchFileException) {
    mcpFail("File not found: $pathInProject")
  }
  if (!attributes.isRegularFile) {
    mcpFail("Not a file: $pathInProject")
  }
  return resolvedPath
}
