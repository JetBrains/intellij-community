// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Version
import com.jetbrains.python.packaging.PyPackageName

/**
 * A Python tool the IDE can locate, install and upgrade.
 *
 * What it resolves to is per Eel machine, as [PyExecutable] describes: the custom path and the detection
 * cache are keyed by machine, so the same tool is one binary on the host and another in WSL or a container.
 * Nothing here is per project, which is why no member takes one.
 *
 * Everything scoped to a project — the enabled flag, the configuration, the lifecycle hooks — belongs to
 * [ProjectLevelPyTool], which a tool that has any of it mixes in. A package manager does not.
 */
interface PyTool : PyExecutable {
  /** The normalized Python package that installs this tool. */
  val packageName: PyPackageName

  /** The package identifier used for feature usage statistics. */
  override val fusId: String get() = packageName.name

  /**
   * All executable commands that this tool provides.
   *
   * Each command has a separate custom path and detection cache entry. The default list contains only this tool.
   */
  val executables: List<PyExecutable> get() = listOf(this)

  /**
   * The manager that installs and upgrades this tool.
   *
   * The default manager uses a Python package. A null value disables installation through the IDE.
   */
  val manager: PyToolManager? get() = PackagePyToolManager

  /**
   * The oldest tool version that this integration supports.
   *
   * A null value means that the integration has no minimum version.
   */
  val minimumSupportedVersion: Version? get() = null

  companion object {
    val EP_NAME: ExtensionPointName<PyTool> = ExtensionPointName.create("com.intellij.python.pytools.pyTool")

    /** Finds a tool by its normalized Python package name. */
    fun findByPackageName(packageName: String): PyTool? {
      val normalized = PyPackageName.from(packageName).name
      return EP_NAME.extensionList.firstOrNull { it.packageName.name == normalized }
    }

    /** Finds a primary or secondary executable command by its identifier. */
    fun findExecutable(name: String): PyExecutable? =
      EP_NAME.extensionList.firstNotNullOfOrNull { tool -> tool.executables.firstOrNull { it.fusId == name } }
  }
}

/** Marks a backend tool as a package manager that the Package Managers page can detect. */
interface PackageManagerPyTool : PyTool
