// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.common.PyToolDescriptorDto
import com.intellij.python.pytools.common.PyToolId
import com.intellij.python.pytools.common.PyToolPathDto
import com.intellij.python.pytools.common.PyToolPathKind
import com.intellij.python.pytools.common.PyToolStateDto
import com.intellij.python.requirements.PyPackageVersionComparator
import java.nio.file.Path

/**
 * One tool's full state for the settings pages, shared by the two API providers.
 *
 * Lives outside both because each has a call that answers with a whole state: an install, upgrade or path
 * change on `PyToolApi`, an enable toggle on `ProjectLevelPyToolApi`.
 */
internal suspend fun buildToolState(
  project: Project,
  executable: PyExecutable,
  knownPath: Path? = null,
  installed: InstalledInfo? = null,
): PyToolStateDto {
  val descriptor = project.getEelDescriptor()
  val custom = executable.getCustomExecutablePath(descriptor)
  val path = custom ?: knownPath ?: PyExecutableCache.getInstance().get(descriptor, executable)
  // What the manager reports counts only when the path resolved above is the very file it installed. Another
  // path can hold a different build of the tool, or another program altogether, and an upgrade through the
  // manager would not touch it. A path the manager reports through a symlink compares unequal here, which
  // costs a version probe but never reports a version or an upgrade of a different file.
  val managed = installed?.takeIf { path != null && it.path.normalize() == path.normalize() }
  val details = when (executable) {
    is PyTool -> PyToolDetails(
      // Only the version the manager already knows. Running `<path> --version` for every tool of a page
      // costs a process per tool, so a version the manager cannot supply is asked for through getVersion,
      // where the page shows it.
      version = managed?.installedVersion,
      minimumSupportedVersion = executable.minimumSupportedVersion?.toCompactString(),
      support = executable.manager?.support(descriptor) ?: PyToolSupport.NONE,
      selectedAsTypeEngine = (executable as? ProjectLevelPyTool<*>)?.isSelectedAsTypeEngine(project) == true,
    )
    else -> PyToolDetails()
  }
  return PyToolStateDto(
    toolId = PyToolId(executable.fusId),
    descriptor = PyToolDescriptorDto(details.minimumSupportedVersion),
    enabled = PyToolsState.getInstance(project).isEnabled(PyToolId(executable.fusId)),
    path = when {
      custom != null -> PyToolPathDto(custom.toString(), PyToolPathKind.CUSTOM)
      path != null -> PyToolPathDto(path.toString(), PyToolPathKind.DETECTED)
      else -> null
    },
    version = details.version,
    canInstall = details.support.canInstall,
    // `uv tool list --outdated` repeats the installed version when a tool is up to date; report an
    // upgrade only when the latest one is actually newer, and only for a tool the IDE can actually upgrade.
    latestVersion = managed?.latestVersion?.takeIf {
      details.support.canUpgrade && PyPackageVersionComparator.STR_COMPARATOR.compare(it, managed.installedVersion) > 0
    },
    selectedAsTypeEngine = details.selectedAsTypeEngine,
  )
}

private data class PyToolDetails(
  val version: String? = null,
  val minimumSupportedVersion: String? = null,
  val support: PyToolSupport = PyToolSupport.NONE,
  val selectedAsTypeEngine: Boolean = false,
)
