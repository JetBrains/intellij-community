package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

val Module.baseDir: VirtualFile?
  get() = rootManager.contentRoots.firstOrNull()

val Module.basePath: String?
  get() = baseDir?.path