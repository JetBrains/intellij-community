// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.backend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.backend.statistics.PyToolFusSnapshot
import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.jetbrains.python.packaging.PyPackageName

/** Defines the backend behavior and state for a Python tool. */
interface PyTool<C : PyToolConfigurationDto> : PyExecutable {
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

  /**
   * Migrates the old project state when [PyToolsState] has no stored state.
   *
   * The implementation must reset the old state. This reset makes the migration one-way.
   */
  fun migrateLegacyState(project: Project): PyToolsState.ToolEntry? = null

  /** Handles an applied change to the enabled state. An LSP tool can start or stop its server here. */
  fun onEnabledChanged(project: Project, enabled: Boolean) {}

  /**
   * Returns true when the project uses this tool as its type engine.
   *
   * An active type engine remains active when its separate enabled state is false.
   */
  fun isSelectedAsTypeEngine(project: Project): Boolean = false

  /** Returns the serializable tool configuration for the frontend. */
  fun configurationState(project: Project): C? = null

  /** Applies a serializable tool configuration from the frontend. */
  fun applyConfigurationState(project: Project, state: C) {}

  /**
   * Returns all configuration data that this tool records for feature usage statistics.
   *
   * The default snapshot contains the enabled state and the presence of a custom path. A tool can add its feature settings.
   */
  fun configurationFusSnapshot(project: Project): PyToolFusSnapshot = PyToolFusSnapshot(
    enabled = PyToolsState.getInstance(project).isEnabled(this),
    customPath = getCustomExecutablePath(project.getEelDescriptor()) != null,
  )

  companion object {
    val EP_NAME: ExtensionPointName<PyTool<*>> = ExtensionPointName.create("com.intellij.python.pytools.pyTool")

    /** Finds a tool by its normalized Python package name. */
    fun findByPackageName(packageName: String): PyTool<*>? {
      val normalized = PyPackageName.from(packageName).name
      return EP_NAME.extensionList.firstOrNull { it.packageName.name == normalized }
    }

    /** Finds a primary or secondary executable command by its identifier. */
    fun findExecutable(name: String): PyExecutable? =
      EP_NAME.extensionList.firstNotNullOfOrNull { tool -> tool.executables.firstOrNull { it.fusId == name } }
  }
}

/** Marks a backend tool as a package manager that the Package Managers page can detect. */
interface PackageManagerPyTool : PyTool<PyToolConfigurationDto>

/** Returns true when the user enabled this tool for the project. */
fun PyTool<*>.isEnabledOn(project: Project): Boolean = PyToolsState.getInstance(project).isEnabled(this)

/** Returns true when the tool is enabled or selected as the project type engine. */
fun PyTool<*>.isActiveOn(project: Project): Boolean = isEnabledOn(project) || isSelectedAsTypeEngine(project)
