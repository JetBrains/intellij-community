package com.jetbrains.python.sdk

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.FileName
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@get:Deprecated("Use root manager directly and obey its contract",
                replaceWith = ReplaceWith(" ModuleRootManager.getInstance(this)"),
                level = DeprecationLevel.ERROR)
val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

/**
 * It could be loosely described as a "root directory" of a module. Something, you usually look for `pyproject.toml` in.
 * `null` means module is broken: i.e. not a Python module (and doesn't have a baseDir) or it was already disposed, or directory
 * was removed. Such modules should be ignored.
 */
val Module.baseDir: VirtualFile?
  get() {
    val entity = findModuleEntity() ?: return null
    val vfsMan = VirtualFileManager.getInstance()
    val roots = entity.contentRoots.asSequence().mapNotNull { vfsMan.findFileByUrl(it.url.url) }
    val moduleFile = moduleFile ?: return roots.firstOrNull()
    return roots.firstOrNull { VfsUtil.isAncestor(it, moduleFile, true) } ?: roots.firstOrNull()
  }


@get:Deprecated("Representing path as string is discouraged", replaceWith = ReplaceWith("baseBase?.path"), level = DeprecationLevel.ERROR)
val Module.basePath: String?
  get() = baseDir?.path

@Internal
suspend fun findAmongRoots(module: Module, fileName: String): VirtualFile? = readAction {
  // to prevent module disposition as disposed module provided to root manager throws an exception
  if (module.isDisposed) {
    logger.warn("Module $module is disposed, and can't have $fileName")
  }
  else {
    for (root in ModuleRootManager.getInstance(module).contentRoots) {
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