package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntityIfNotDisposed
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This function is obsolete: Use [com.jetbrains.python.project.PyProject] instead.
 *
 * It could be loosely described as a "root directory" of a module. Something, you usually look for `pyproject.toml` in.
 * `null` means module is broken: i.e. not a Python module (and doesn't have a baseDir) or it was already disposed, or directory
 * was removed. Such modules should be ignored.
 */
@get:Internal
@Deprecated("Use PyProject instead")
val Module.baseDir: VirtualFile?
  get() {
    val entity = findModuleEntityIfNotDisposed()
    val roots = entity.contentRoots.asSequence().mapNotNull { it.url.virtualFile }
    val moduleFile = moduleFile ?: return roots.firstOrNull()
    return roots.firstOrNull { VfsUtil.isAncestor(it, moduleFile, true) } ?: roots.firstOrNull()
  }
