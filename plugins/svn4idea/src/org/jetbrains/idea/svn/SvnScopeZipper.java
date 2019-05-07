// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

public class SvnScopeZipper implements Runnable {

  @NotNull private final VcsDirtyScope myIn;
  @NotNull private final List<FilePath> myRecursiveDirs;
  @NotNull private final MultiMap<FilePath, FilePath> myNonRecursiveDirs = MultiMap.createSet();

  public SvnScopeZipper(@NotNull VcsDirtyScope in) {
    myIn = in;
    myRecursiveDirs = newArrayList(in.getRecursivelyDirtyDirectories());
  }

  @Override
  public void run() {
    // if put directly into dirty scope, to access a copy will be created every time
    Set<FilePath> files = myIn.getDirtyFilesNoExpand();

    for (FilePath file : files) {
      if (file.isDirectory()) {
        VirtualFile vFile = file.getVirtualFile();
        // todo take care about this 'not valid' - right now keeping things as they used to be
        if (vFile != null && vFile.isValid()) {
          for (VirtualFile child : vFile.getChildren()) {
            myNonRecursiveDirs.putValue(file, VcsUtil.getFilePath(child));
          }
        }
      }
      else {
        FilePath parent = file.getParentPath();
        if (parent != null) {
          myNonRecursiveDirs.putValue(parent, file);
        }
      }
    }
  }

  @NotNull
  public List<FilePath> getRecursiveDirs() {
    return myRecursiveDirs;
  }

  @NotNull
  public MultiMap<FilePath, FilePath> getNonRecursiveDirs() {
    return myNonRecursiveDirs;
  }
}
