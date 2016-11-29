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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class UniqueRootsFilter {

  public <T extends RootUrlPair> List<T> filter(@NotNull final List<T> list) {
    List<T> result = new ArrayList<>();

    sort(list);
    for (final T child : list) {
      if (!alreadyRegistered(child, result)) {
        result.add(child);
      }
    }

    return result;
  }

  private static <T extends RootUrlPair> void sort(List<T> list) {
    Collections.sort(list, new Comparator<RootUrlPair>() {
      public int compare(final RootUrlPair o1, final RootUrlPair o2) {
        return o1.getVirtualFile().getPath().compareTo(o2.getVirtualFile().getPath());
      }
    });
  }

  private static <T extends RootUrlPair> boolean alreadyRegistered(@NotNull final T child, @NotNull List<T> registered) {
    return ContainerUtil.exists(registered, new Condition<T>() {
      @Override
      public boolean value(T parent) {
        return isSamePath(child, parent) || isSameSupposedUrl(child, parent);
      }
    });
  }

  private static <T extends RootUrlPair> boolean isSamePath(@NotNull T child, @NotNull T parent) {
    return parent.getVirtualFile().getPath().equals(child.getVirtualFile().getPath());
  }

  private static <T extends RootUrlPair> boolean isSameSupposedUrl(@NotNull T child, @NotNull T parent) {
    boolean result = false;

    if (VfsUtilCore.isAncestor(parent.getVirtualFile(), child.getVirtualFile(), true)) {
      String relativePath = VfsUtilCore.getRelativePath(child.getVirtualFile(), parent.getVirtualFile(), '/');
      // get child's supposed and real urls
      final String supposed = getSupposedUrl(parent.getUrl(), relativePath);
      if (supposed.equals(child.getUrl())) {
        result = true;
      }
    }

    return result;
  }

  @Nullable
  private static String getSupposedUrl(final String parentUrl, final String relativePath) {
    if (parentUrl == null) return null;

    return SVNPathUtil.append(parentUrl, relativePath);
  }
}
