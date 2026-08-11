// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonSdkModuleRoots")

package com.jetbrains.python.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
import org.jetbrains.annotations.ApiStatus

/**
 * Returns content roots of all python modules in [Project]
 * Awaits workspace-model synchronization with the on-disk JPS model first (PY-86494), then
 * reads modules directly from the immutable workspace-model snapshot — no read action needed.
 */
@ApiStatus.Internal
suspend fun Project.getModuleRoots(): Set<VirtualFile> =
  getPyProjects().mapNotNull { LocalFileSystem.getInstance().findFileByNioFile(it.baseDir) }.toSet()
