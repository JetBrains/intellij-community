package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.util.resolveInProject
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

@Internal
internal fun resolveExistingRegularFileInProject(pathInProject: String, projectDirectory: Path): Path {
  val resolvedPath = resolveInProject(pathInProject = pathInProject, projectDirectory = projectDirectory)
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
