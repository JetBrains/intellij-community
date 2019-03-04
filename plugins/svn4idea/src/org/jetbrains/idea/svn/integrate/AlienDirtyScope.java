// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AlienDirtyScope extends VcsDirtyScope {
  private final Set<FilePath> myFiles;
  private final Set<FilePath> myDirs;

  public AlienDirtyScope() {
    myFiles = new HashSet<>();
    myDirs = new HashSet<>();
  }

  @Override
  public Collection<VirtualFile> getAffectedContentRoots() {
    return null;
  }

  @Override
  public Project getProject() {
    return null;
  }

  @Override
  public AbstractVcs getVcs() {
    return null;
  }

  @Override
  public Set<FilePath> getDirtyFiles() {
    return myFiles;
  }

  @Override
  public Set<FilePath> getDirtyFilesNoExpand() {
    return myFiles;
  }

  @Override
  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirs;
  }

  @Override
  public boolean isRecursivelyDirty(final VirtualFile vf) {
    for (FilePath dir : myDirs) {
      final VirtualFile dirVf = dir.getVirtualFile();
      if (dirVf != null) {
        if (VfsUtil.isAncestor(dirVf, vf, false)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void iterate(final Processor<? super FilePath> iterator) {
  }

  @Override
  public void iterateExistingInsideScope(Processor<? super VirtualFile> vf) {
  }

  @Override
  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }

  @Override
  public boolean belongsTo(final FilePath path) {
    return false;
  }

  @Override
  public boolean belongsTo(FilePath path, Consumer<? super AbstractVcs> vcsConsumer) {
    return false;
  }

  public void addFile(final FilePath path) {
    myFiles.add(path);
  }

  public void addDir(final FilePath dir) {
    myDirs.add(dir);
  }
}
