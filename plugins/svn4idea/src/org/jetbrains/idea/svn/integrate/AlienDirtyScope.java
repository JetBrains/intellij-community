/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public Collection<VirtualFile> getAffectedContentRoots() {
    return null;
  }

  public Project getProject() {
    return null;
  }

  public AbstractVcs getVcs() {
    return null;
  }

  public Set<FilePath> getDirtyFiles() {
    return myFiles;
  }

  public Set<FilePath> getDirtyFilesNoExpand() {
    return myFiles;
  }

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

  public void iterate(final Processor<FilePath> iterator) {
  }

  @Override
  public void iterateExistingInsideScope(Processor<VirtualFile> vf) {
  }

  @Override
  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }

  public boolean belongsTo(final FilePath path) {
    return false;
  }

  @Override
  public boolean belongsTo(FilePath path, Consumer<AbstractVcs> vcsConsumer) {
    return false;
  }

  public void addFile(final FilePath path) {
    myFiles.add(path);
  }

  public void addDir(final FilePath dir) {
    myDirs.add(dir);
  }
}
