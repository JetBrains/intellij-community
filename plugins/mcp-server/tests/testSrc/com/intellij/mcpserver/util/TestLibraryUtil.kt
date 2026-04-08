package com.intellij.mcpserver.util

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

suspend fun attachJarLibrary(
  project: Project,
  module: Module,
  jarFixture: VirtualFile,
  libraryName: String = jarFixture.nameWithoutExtension,
) {
  edtWriteAction {
    ModuleRootModificationUtil.addModuleLibrary(
      module,
      libraryName,
      listOf(VfsUtil.getUrlForLibraryRoot(java.io.File(jarFixture.path))),
      emptyList(),
    )
  }
  awaitExternalChangesAndIndexing(project)
  DumbService.getInstance(project).waitForSmartMode()
}

fun findJarLibraryEntry(jarFixture: VirtualFile, entryRelativePath: String): VirtualFile {
  val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFixture)
                ?: error("Cannot resolve jar root for ${jarFixture.path}")
  return jarRoot.findFileByRelativePath(entryRelativePath)
         ?: error("Cannot find $entryRelativePath in ${jarFixture.path}")
}
