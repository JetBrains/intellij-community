package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

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

val Module.modulePath: String?
  get() {
    var moduleDirectory = File(moduleFilePath).parentFile
    if (moduleDirectory.name == ".idea") {
      moduleDirectory = moduleDirectory.parentFile
    }
    return moduleDirectory?.path
  }