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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SvnScopeZipper implements Runnable {

  @NotNull private final VcsDirtyScope myIn;
  @NotNull private final List<FilePath> myRecursiveDirs;
  // instead of set and heavy equals of file path
  @NotNull private final Map<String, MyDirNonRecursive> myNonRecursiveDirs;

  public SvnScopeZipper(@NotNull VcsDirtyScope in) {
    myIn = in;
    myRecursiveDirs = ContainerUtil.newArrayList(in.getRecursivelyDirtyDirectories());
    myNonRecursiveDirs = ContainerUtil.newHashMap();
  }

  public void run() {
    // if put directly into dirty scope, to access a copy will be created every time
    final Set<FilePath> files = myIn.getDirtyFilesNoExpand();

    for (FilePath file : files) {
      if (file.isDirectory()) {
        final VirtualFile vFile = file.getVirtualFile();
        // todo take care about this 'not valid' - right now keeping things as they used to be
        final MyDirNonRecursive me = createOrGet(file);
        if (vFile != null && vFile.isValid()) {
          for (VirtualFile child : vFile.getChildren()) {
            me.add(VcsUtil.getFilePath(child));
          }
        }
      }
      else {
        final FilePath parent = file.getParentPath();
        if (parent != null) {
          final MyDirNonRecursive item = createOrGet(parent);
          item.add(file);
        }
      }
    }
  }

  @NotNull
  private MyDirNonRecursive createOrGet(@NotNull FilePath parent) {
    String key = getKey(parent);
    MyDirNonRecursive result = myNonRecursiveDirs.get(key);

    if (result == null) {
      result = new MyDirNonRecursive(parent);
      myNonRecursiveDirs.put(key, result);
    }

    return result;
  }

  @NotNull
  public List<FilePath> getRecursiveDirs() {
    return myRecursiveDirs;
  }

  @NotNull
  public Map<String, MyDirNonRecursive> getNonRecursiveDirs() {
    return myNonRecursiveDirs;
  }

  public static String getKey(@NotNull FilePath path) {
    return path.getPresentableUrl();
  }

  static class MyDirNonRecursive {

    @NotNull private final FilePath myDir;
    // instead of set and heavy equals of file path
    @NotNull private final Map<String, FilePath> myChildren;

    private MyDirNonRecursive(@NotNull FilePath dir) {
      myDir = dir;
      myChildren = ContainerUtil.newHashMap();
    }

    public void add(@NotNull FilePath path) {
      myChildren.put(getKey(path), path);
    }

    @NotNull
    public Collection<FilePath> getChildrenList() {
      return myChildren.values();
    }

    @NotNull
    public FilePath getDir() {
      return myDir;
    }
  }
}
