// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.integrate
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.SvnVcs

class AlienDirtyScope(private val vcs: SvnVcs) : VcsDirtyScope() {
  private val files = mutableSetOf<FilePath>()
  private val dirs = mutableSetOf<FilePath>()

  override fun getAffectedContentRoots(): Collection<VirtualFile> = emptyList()

  override fun getProject(): Project = vcs.project
  override fun getVcs(): AbstractVcs = vcs

  override fun getDirtyFiles(): Set<FilePath> = files
  override fun getDirtyFilesNoExpand(): Set<FilePath> = files
  override fun getRecursivelyDirtyDirectories(): Set<FilePath> = dirs

  override fun isEmpty(): Boolean = files.isEmpty() && dirs.isEmpty()
  override fun belongsTo(path: FilePath): Boolean = false
  override fun wasEveryThingDirty(): Boolean = false

  fun addFile(path: FilePath) {
    files += path
  }

  fun addDir(dir: FilePath) {
    dirs += dir
  }
}