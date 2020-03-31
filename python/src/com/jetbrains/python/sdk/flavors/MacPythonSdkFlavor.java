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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class MacPythonSdkFlavor extends CPythonSdkFlavor {
  private MacPythonSdkFlavor() {
  }

  private static final String[] POSSIBLE_BINARY_NAMES = {"python", "python2", "python3"};

  @Override
  public boolean isApplicable() {
    return SystemInfo.isMac;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    Set<String> candidates = new HashSet<>();
    collectPythonInstallations("/Library/Frameworks/Python.framework/Versions", candidates);
    collectPythonInstallations("/System/Library/Frameworks/Python.framework/Versions", candidates);
    collectPythonInstallations("/usr/local/Cellar/python", candidates);
    UnixPythonSdkFlavor.collectUnixPythons("/usr/local/bin", candidates);
    UnixPythonSdkFlavor.collectUnixPythons("/usr/bin", candidates);
    return candidates;
  }

  private static void collectPythonInstallations(String pythonPath, Set<String> candidates) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
    if (rootVDir != null) {
      if (rootVDir instanceof NewVirtualFile) {
        ((NewVirtualFile)rootVDir).markDirty();
      }
      rootVDir.refresh(true, false);
      for (VirtualFile dir : rootVDir.getChildren()) {
        final String dirName = StringUtil.toLowerCase(dir.getName());
        if (dir.isDirectory()) {
          if ("Current".equals(dirName) || dirName.startsWith("2") || dirName.startsWith("3")) {
            final VirtualFile binDir = dir.findChild("bin");
            if (binDir != null && binDir.isDirectory()) {
              for (String name : POSSIBLE_BINARY_NAMES) {
                final VirtualFile child = binDir.findChild(name);
                if (child == null) continue;
                final String path = child.getPath();
                if (!candidates.contains(path)) {
                  candidates.add(path);
                  break;
                }
              }
            }
          }
        }
      }
    }
  }
}
