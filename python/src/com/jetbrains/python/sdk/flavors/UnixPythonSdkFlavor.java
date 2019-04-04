/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author yole
 */
public class UnixPythonSdkFlavor extends CPythonSdkFlavor {
  private UnixPythonSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"jython", "pypy"};

  public static final UnixPythonSdkFlavor INSTANCE = new UnixPythonSdkFlavor();

  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module) {
    Set<String> candidates = new HashSet<>();
    collectUnixPythons("/usr/bin", candidates);
    return candidates;
  }

  public static void collectUnixPythons(String path, Set<? super String> candidates) {
    VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(path);
    if (rootDir != null) {
      if (rootDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootDir).markDirty();
      }
      rootDir.refresh(true, false);
      VirtualFile[] suspects = rootDir.getChildren();
      for (VirtualFile child : suspects) {
        if (!child.isDirectory()) {
          final String childName = child.getName().toLowerCase(Locale.US);
          for (String name : NAMES) {
            if (childName.startsWith(name) || PYTHON_RE.matcher(childName).matches()) {
              final String childPath = child.getPath();
              if (!childName.endsWith("-config") &&
                  !childName.startsWith("pythonw") &&
                  !childName.endsWith("m")) {
                candidates.add(childPath);
              }
              break;
            }
          }
        }
      }
    }
  }
}
