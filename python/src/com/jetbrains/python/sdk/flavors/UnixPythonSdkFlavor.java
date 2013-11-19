/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class UnixPythonSdkFlavor extends CPythonSdkFlavor {
  private UnixPythonSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"python", "jython", "pypy"};

  public static UnixPythonSdkFlavor INSTANCE = new UnixPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths() {
    List<String> candidates = new ArrayList<String>();
    collectUnixPythons("/usr/bin", candidates);
    return candidates;
  }

  public static void collectUnixPythons(String path, List<String> candidates) {
    VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(path);
    if (rootDir != null) {
      if (rootDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootDir).markDirty();
      }
      rootDir.refresh(false, false);
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (!child.isDirectory()) {
          final String childName = child.getName();
          for (String name : NAMES) {
            if (childName.startsWith(name)) {
              if (!childName.endsWith("-config") && !childName.startsWith("pythonw")) {
                candidates.add(child.getPath());
              }
              break;
            }
          }
        }
      }
    }
  }
}
