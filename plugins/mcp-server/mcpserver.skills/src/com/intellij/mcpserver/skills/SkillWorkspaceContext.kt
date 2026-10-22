package com.intellij.mcpserver.skills

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** The normalized agent working directory and the open IDE project at that directory, if any. */
@ApiStatus.Experimental
class SkillWorkspaceContext private constructor(
  val workingDirectory: Path,
  /** An open IDE project whose normalized base path equals [workingDirectory]. */
  val project: Project?,
) {
  companion object {
    fun create(workingDirectory: Path, project: Project?): SkillWorkspaceContext {
      val normalizedPath = workingDirectory.toAbsolutePath().normalize()
      val openProject = project?.takeIf { it.isOpen && !it.isDisposed && !it.isDefault }
      val projectPath = openProject?.basePath?.let { Path.of(it).toAbsolutePath().normalize() }
      val exactProject = openProject?.takeIf { projectPath == normalizedPath }
      return SkillWorkspaceContext(normalizedPath, exactProject)
    }
  }
}
