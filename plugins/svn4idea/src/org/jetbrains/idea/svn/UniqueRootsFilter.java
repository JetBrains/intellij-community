/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.getRelativePath;
import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static java.util.Collections.sort;
import static java.util.Comparator.comparing;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.append;

public class UniqueRootsFilter {

  @NotNull
  public <T extends RootUrlPair> List<T> filter(@NotNull List<T> list) {
    List<T> result = newArrayList();

    sort(list, comparing(item -> item.getVirtualFile().getPath()));
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

    return relativePath != null && append(parent.getUrl(), relativePath).equals(child.getUrl());
  }
}
