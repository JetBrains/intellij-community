// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.ignore;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileGroupInfo implements FileIterationListener, IgnoreInfoGetter {
  private String commonExtension;
  private final Map<VirtualFile, Set<String>> folders;

  private int fileCount;
  private boolean sameExtensionCase;

  public FileGroupInfo() {
    fileCount = 0;
    sameExtensionCase = true;
    folders = new HashMap<>();
  }

  @Override
  public void onFileEnabled(final VirtualFile file) {
    ++ fileCount;

    if (sameExtensionCase) {
      final String extension = file.getExtension();
      if (extension == null) {
        sameExtensionCase = false;
      } else if (commonExtension == null) {
        commonExtension = extension;
      } else {
        sameExtensionCase &= commonExtension.equals(extension);
      }
    }

    final VirtualFile parentVirtualFile = file.getParent();

    if (parentVirtualFile != null) {
      Set<String> namesList = folders.get(parentVirtualFile);
      if (namesList == null) {
        namesList = new HashSet<>();
        folders.put(parentVirtualFile, namesList);
      }
      namesList.add(file.getName());
    }
  }

  public boolean oneFileSelected() {
    return fileCount == 1;
  }

  public boolean sameExtension() {
    return sameExtensionCase;
  }

  public @NlsSafe @NotNull String getExtensionMask() {
    return "*." + commonExtension;
  }

  @Override
  public Map<VirtualFile, Set<String>> getInfo(final boolean useCommonExtension) {
    if (! useCommonExtension) {
      return folders;
    }
    final Map<VirtualFile, Set<String>> result = new HashMap<>(folders.size(), 1);
    for (final Map.Entry<VirtualFile, Set<String>> entry : folders.entrySet()) {
      final Set<String> set = new HashSet<>();
      set.add(getExtensionMask());
      result.put(entry.getKey(), set);
    }
    return result;
  }

  public @NlsSafe @NotNull String getFileName() {
    return folders.values().iterator().next().iterator().next();
  }
}
