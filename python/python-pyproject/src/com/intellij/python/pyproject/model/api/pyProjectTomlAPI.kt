package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.internal.getPyProjectTomlFileImpl
import com.intellij.python.pyproject.model.internal.isPyProjectTomlBasedImpl
import com.intellij.python.pyproject.model.internal.workspaceBridge.cleanupPyProjectBasedModulesImpl
import com.intellij.python.pyproject.model.internal.workspaceBridge.getPythonDependenciesImpl
import com.jetbrains.python.project.PyProject


/**
 * If module was generated from `pyproject.toml`
 */
val Module.isPyProjectTomlBased: Boolean get() = isPyProjectTomlBasedImpl

/**
 * Returns the `pyproject.toml` [VirtualFile] backing this module (via the workspace bridge), or
 * `null` if the module is not pyproject-based or the file is not currently indexed in VFS.
 *
 * Acquires a read action internally to guarantee the returned [VirtualFile] is valid
 * (see [VirtualFile.findChild]). Callers do not need to wrap the call in a read action.
 */
suspend fun Module.getPyProjectTomlFile(): VirtualFile? = getPyProjectTomlFileImpl()

/**
 * Removes every `pyproject.toml`-generated Python module from the workspace. Source roots and
 * excluded folders of each removed module are relocated to the deepest containing non-pyproject
 * module, so folder marks (Sources, Resources, Excluded, …) survive under the filesystem parent.
 *
 * Call when the user turns off auto-configuration from `pyproject.toml` so the workspace reverts
 * to the user's manual module structure without losing per-folder settings.
 */
suspend fun cleanupPyProjectBasedModules(project: Project): Unit = cleanupPyProjectBasedModulesImpl(project)

/**
 * The [PyProject]s this project depends on through its backing module's order entries, intersected
 * with every [PyProject] in the enclosing [Project] — non-python order entries are dropped, so the
 * caller never sees a raw `Module` on this seam.
 *
 * Reads `ModuleRootManager.dependencies` under a `readAction`; callers do not need to wrap this.
 */
suspend fun PyProject.getPythonDependencies(): Set<PyProject> = getPythonDependenciesImpl()
