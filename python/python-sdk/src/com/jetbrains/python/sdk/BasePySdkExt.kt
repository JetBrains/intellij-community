package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.FileName
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

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
suspend fun findAmongRoots(module: Module, fileName: String): VirtualFile? = readAction {
  // to prevent module disposition as disposed module provided to root manager throws an exception
  if (module.isDisposed) {
    logger.warn("Module $module is disposed, and can't have $fileName")
  }
  else {
    for (root in module.rootManager.contentRoots) {
      val file = root.findChild(fileName)
      if (file != null) {
        return@readAction file
      }
    }
  }
  null
}

@Internal
suspend fun Module.findAmongRoots(fileName: FileName): Path? = findAmongRoots(this, fileName.value)?.toNioPath()

private val logger = fileLogger()