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
