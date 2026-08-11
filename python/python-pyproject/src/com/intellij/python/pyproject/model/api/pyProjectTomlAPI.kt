package com.intellij.python.pyproject.model.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.internal.getPyProjectTomlFileImpl
import com.intellij.python.pyproject.model.internal.isPyProjectTomlBasedImpl


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
