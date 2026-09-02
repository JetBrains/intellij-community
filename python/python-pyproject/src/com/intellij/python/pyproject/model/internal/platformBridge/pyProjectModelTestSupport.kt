// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.python.pyproject.model.internal.workspaceBridge.collectExcludedPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jetbrains.python.sdk.baseDir
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Rebuilds the `pyproject.toml` model at once, for a test.
 *
 * A test often writes a `pyproject.toml` with `java.nio`, and the VFS does not see such a file.
 * The production code needs no refresh here, because it waits for the initial VFS refresh and it drills
 * into every new directory. A test skips both steps, so this helper loads the project tree first.
 */
@TestOnly
@ApiStatus.Internal
suspend fun rebuildPyProjectModelForTest(project: Project) {
  refreshProjectRootsIntoVfs(project)
  project.service<PyProjectModelSyncService>().rebuildNow()
}

/** Loads the project tree into the VFS, so the filename index sees a file that a test wrote with `java.nio`. */
@TestOnly
@ApiStatus.Internal
suspend fun refreshProjectRootsIntoVfs(project: Project) {
  val localFileSystem = LocalFileSystem.getInstance()
  val roots = withContext(Dispatchers.IO) {
    val paths = sequenceOf(project.stateStore.projectBasePath) + project.modules.asSequence().mapNotNull { it.baseDir?.toNioPath() }
    computeMinimalRoots(paths).mapNotNull { localFileSystem.refreshAndFindFileByNioFile(it) }
  }
  VfsUtil.markDirtyAndRefresh(false, true, true, *roots.toTypedArray())
  loadSubtreesIntoVfs(LinkedHashSet<VirtualFile>(roots), collectExcludedPaths(project))
}
