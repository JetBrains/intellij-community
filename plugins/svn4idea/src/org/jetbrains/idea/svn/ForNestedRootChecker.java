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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.util.*;

public class ForNestedRootChecker {

  private static class UrlConstructor {
    final SvnVcs myVcs;

    private UrlConstructor(final SvnVcs vcs) {
      myVcs = vcs;
    }

    @Nullable
    public Real createReal(final VirtualFile file, final VirtualFile vcsRoot) {
      final SVNInfo info = myVcs.getInfo(file);
      if (info == null || info.getRepositoryRootURL() == null || info.getURL() == null) {
        return null;
      }
      return new Real(file, info, vcsRoot);
    }

    public Node createReplaceable(final VirtualFile file) {
      return new Node(file, null);
    }

    public Node createSupposed(final Real parent, final VirtualFile child) {
      return new Node(child, getForChild(parent.getUrl(), child.getName()));
    }

    public boolean replaceWithReal(final Real real, final Node supposed) {
      return supposed.getUrl() == null || ((supposed.getUrl() != null) && (! supposed.getUrl().equals(real.getUrl())));
    }

    @Nullable
    private static String getSupposedUrl(final String parentUrl, final String relativePath) {
      if (parentUrl == null) return null;

      return SVNPathUtil.append(parentUrl, relativePath);
    }

    public String getForChild(final String parentUrl, final String childName) {
      return parentUrl == null ? null : SVNPathUtil.append(parentUrl, SVNEncodingUtil.uriEncode(childName));
    }
  }

  public static<T extends RootUrlPair> List<T> filterOutSuperfluousChildren(@NotNull final List<T> list) {
    List<T> result = new ArrayList<T>();

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
      final String supposed = UrlConstructor.getSupposedUrl(parent.getUrl(), relativePath);
      if (supposed.equals(child.getUrl())) {
        result = true;
      }
    }

    return result;
  }

  public static List<Real> getAllNestedWorkingCopies(final VirtualFile root, final SvnVcs vcs, final boolean goIntoNested, final Getter<Boolean> cancelledGetter) {
    final VcsRootIterator rootIterator = new VcsRootIterator(vcs.getProject(), vcs);
    return getForOne(root, vcs, goIntoNested, rootIterator, cancelledGetter);
  }

  private static List<Real> getForOne(final VirtualFile root, final SvnVcs vcs, final boolean goIntoNested,
                                      final VcsRootIterator rootIterator, final Getter<Boolean> cancelledGetter) {
    final UrlConstructor constructor = new UrlConstructor(vcs);
    final LinkedList<Node> queue = new LinkedList<Node>();
    final LinkedList<Real> result = new LinkedList<Real>();

    queue.add(constructor.createReplaceable(root));
    while (! queue.isEmpty()) {
      final Node node = queue.removeFirst();
      if (Boolean.TRUE.equals(cancelledGetter.get())) throw new ProcessCanceledException();

      // check self
      final Real real = constructor.createReal(node.getFile(), root);
      if (real != null) {
        if (constructor.replaceWithReal(real, node)) {
          result.add(real);
          if (! goIntoNested) continue;
        }
      }

      // for next step
      final VirtualFile file = node.getFile();
      if (file.isDirectory() && (! SvnUtil.isAdminDirectory(file))) {
        for (VirtualFile child : file.getChildren()) {
          if (Boolean.TRUE.equals(cancelledGetter.get())) throw new ProcessCanceledException();
          if (rootIterator.acceptFolderUnderVcs(root, child)) {
            if (real == null) {
              queue.add(constructor.createReplaceable(child));
            } else {
              queue.add(constructor.createSupposed(real, child));
            }
          }
        }
      }
    }
    return result;
  }
}
