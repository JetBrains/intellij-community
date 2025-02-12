package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.Internal

val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

val Module.baseDir: VirtualFile?
  get() {
    val roots = rootManager.contentRoots
    val moduleFile = moduleFile ?: return roots.firstOrNull()
    return roots.firstOrNull { VfsUtil.isAncestor(it, moduleFile, true) } ?: roots.firstOrNull()
  }

val Module.basePath: String?
  get() = baseDir?.path

@Internal
@RequiresBackgroundThread
fun findAmongRoots(module: Module, fileName: String): VirtualFile? {
  for (root in module.rootManager.contentRoots) {
    val file = root.findChild(fileName)
    if (file != null) return file
  }
  return null
}