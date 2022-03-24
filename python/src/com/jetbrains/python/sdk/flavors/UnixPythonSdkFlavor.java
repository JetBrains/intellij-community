// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


public final class UnixPythonSdkFlavor extends CPythonSdkFlavor {
  private final static Pattern PYTHON_3_RE = Pattern.compile("(python-?3\\.(\\d){1,2})|(python-?3)");

  private UnixPythonSdkFlavor() {
  }

  private final static String[] NAMES = new String[]{"jython", "pypy"};

  public static UnixPythonSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(UnixPythonSdkFlavor.class);
  }

  @Override
  public boolean isApplicable() {
    return SystemInfo.isUnix && !SystemInfo.isMac;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    return getDefaultUnixPythons(null);
  }

  @NotNull
  public static Set<String> getDefaultUnixPythons(@Nullable String rootPrefix) {
    Set<String> candidates = new HashSet<>();
    collectUnixPythons((rootPrefix != null ? rootPrefix : "") + "/usr/bin", candidates);
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
          final String childName = StringUtil.toLowerCase(child.getName());
          if (ArrayUtil.contains(childName, NAMES) || PYTHON_3_RE.matcher(childName).matches()) {
            candidates.add(child.getPath());
          }
        }
      }
    }
  }
}
