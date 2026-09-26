// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.getRelativePath;
import static com.intellij.util.containers.ContainerUtil.exists;
import static java.util.Comparator.comparing;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;

public class UniqueRootsFilter {

  public @NotNull <T extends RootUrlPair> List<T> filter(@NotNull List<T> list) {
    List<T> result = new ArrayList<>();

    list.sort(comparing(item -> item.getVirtualFile().getPath()));
    for (T child : list) {
      if (!alreadyRegistered(child, result)) {
        result.add(child);
      }
    }

    return result;
  }

  private static <T extends RootUrlPair> boolean alreadyRegistered(@NotNull T child, @NotNull List<T> registered) {
    return exists(registered, parent -> isSamePath(child, parent) || isSameSupposedUrl(child, parent));
  }

  private static <T extends RootUrlPair> boolean isSamePath(@NotNull T child, @NotNull T parent) {
    return parent.getVirtualFile().getPath().equals(child.getVirtualFile().getPath());
  }

  private static <T extends RootUrlPair> boolean isSameSupposedUrl(@NotNull T child, @NotNull T parent) {
    String relativePath = getRelativePath(child.getVirtualFile(), parent.getVirtualFile(), '/');

    return relativePath != null && relativePath.equals(getRelativeUrl(parent.getUrl(), child.getUrl()));
  }
}
