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
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.util.*;

public class ForNestedRootChecker {
  static class DirInfo {
    private final VirtualFile myFile;
    private final String myUrl;

    DirInfo(final VirtualFile file, final String url) {
      myFile = file;
      myUrl = url;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public String getUrl() {
      return myUrl;
    }
  }

  private static class UrlConstructor {
    final SvnVcs myVcs;
    final SVNWCClient myClient;

    private UrlConstructor(final SvnVcs vcs) {
      myVcs = vcs;
      myClient = myVcs.createWCClient();
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
    private String getSupposedUrl(final String parentUrl, final List<String> subpath) {
      if (parentUrl == null) return null;
      final StringBuilder sb = new StringBuilder();
      for (String s : subpath) {
        sb.append(s).append('/');
      }
      if (sb.length() > 0) {
        sb.deleteCharAt(sb.length() - 1);
      }
      return SVNPathUtil.append(parentUrl, sb.toString());
    }

    public String getForChild(final String parentUrl, final String childName) {
      return parentUrl == null ? null : SVNPathUtil.append(parentUrl, SVNEncodingUtil.uriEncode(childName));
    }
  }

  public static<T extends RootUrlPair> void filterOutSuperfluousChildren(final SvnVcs vcs, final List<T> list,
                                                                               final List<T> result) {
    final UrlConstructor constructor = new UrlConstructor(vcs);

    Collections.sort(list, new Comparator<RootUrlPair>() {
      public int compare(final RootUrlPair o1, final RootUrlPair o2) {
        return o1.getVirtualFile().getPath().compareTo(o2.getVirtualFile().getPath());
      }
    });

    for (int i = 0; i < list.size(); i++) {
      final T child = list.get(i);
      boolean add = true;
      for (T parent : result) {
        if (parent.getVirtualFile().getPath().equals(child.getVirtualFile().getPath())) {
          add = false;
          break;
        }
        final List<String> subpath = subpathIfAncestor(parent.getVirtualFile(), child.getVirtualFile());
        if (subpath != null) {
          // get child's supposed and real urls
          final String supposed = constructor.getSupposedUrl(parent.getUrl(), subpath);
          if (supposed.equals(child.getUrl())) {
            add = false;
            break;
          }
        }
      }
      if (add) {
        result.add(child);
      }
    }
  }

  @Nullable
  private static List<String> subpathIfAncestor(final VirtualFile parent, final VirtualFile child) {
    if (! VfsUtil.isAncestor(parent, child, true)) return null;

    final List<String> result = new ArrayList<String>();
    VirtualFile tmp = child;
    final String parentPath = parent.getPath();
    while ((tmp != null) && (! tmp.getPath().equals(parentPath))) {
      result.add(tmp.getName());
      tmp = tmp.getParent();
    }
    Collections.reverse(result);
    return result;
  }

  /*public static List<Real> getAllNestedWorkingCopies(final VirtualFile[] roots, final SvnVcs vcs, final boolean goIntoNested) {
    if (goIntoNested) {
      FilterDescendantVirtualFiles.filter(Arrays.asList(roots));
    }

    final VcsRootIterator rootIterator = new VcsRootIterator(vcs.getProject(), vcs);
    final List<Real> result = new ArrayList<Real>();
    for (VirtualFile root : roots) {
      result.addAll(getForOne(root, vcs, goIntoNested, rootIterator));
    }

    if (! goIntoNested) {
      final List<Real> filtered = new ArrayList<Real>(result.size());
      filterOutSuperfluousChildren(vcs, result, filtered);
      return filtered;
    }

    return result;
  }*/

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
