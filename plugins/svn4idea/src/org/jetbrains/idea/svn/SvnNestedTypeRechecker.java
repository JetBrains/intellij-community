/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractFilterChildren;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.*;

// referencies of RootUrlInfo will be kept; so type flag will be raised right in original object
public class SvnNestedTypeRechecker {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnNestedTypeRechecker");

  private final MultiMap<VirtualFile, RootUrlInfo> myRoots;
  private final Map<String, RootUrlInfo> mySourceItems;
  private Map<String, Map<String, File>> myTreeTravellingMap;
  private final Project myProject;

  public SvnNestedTypeRechecker(final Project project, final List<RootUrlInfo> roots) {
    myProject = project;
    final NestedGatherer nestedGatherer = new NestedGatherer();
    nestedGatherer.doFilter(new ArrayList<RootUrlInfo>(roots));
    myRoots = nestedGatherer.getNested();
    mySourceItems = new HashMap<String, RootUrlInfo>();
    for (VirtualFile root : myRoots.keySet()) {
      final Collection<RootUrlInfo> items = myRoots.get(root);
      for (RootUrlInfo item : items) {
        mySourceItems.put(FileUtil.nameToCompare(item.getVirtualFile().getPath()), item);
      }
    }
  }

  private void initTravellingMap() {
    myTreeTravellingMap = new HashMap<String, Map<String, File>>();

    final MultiMap<VirtualFile, File> tmpMap = new MultiMap<VirtualFile, File>();
    for (VirtualFile parent : myRoots.keySet()) {
      final Collection<RootUrlInfo> children = myRoots.get(parent);
      for (RootUrlInfo child : children) {

        VirtualFile current = child.getVirtualFile();
        while (! current.equals(parent)) {
          tmpMap.putValue(current.getParent(), new File(current.getPath()));
          current = current.getParent();
          if (current.getParent() == null) break; //+-
        }
      }
    }

    for (VirtualFile dir : tmpMap.keySet()) {
      final Map<String, File> currMap = new HashMap<String, File>();
      final Collection<File> coll = tmpMap.get(dir);
      for (File file : coll) {
        currMap.put(file.getAbsolutePath(), file);
      }
      myTreeTravellingMap.put(FileUtil.nameToCompare(dir.getPath()), currMap);
    }
  }

  public void run() {
    if (! myRoots.isEmpty()) {
      initTravellingMap();

      final ISVNStatusFileProvider fileProvider = new ISVNStatusFileProvider() {
        public Map getChildrenFiles(File parent) {
          final Map<String, File> children = myTreeTravellingMap.get(FileUtil.nameToCompare(parent.getAbsolutePath()));
          return children == null ? Collections.emptyMap() : children;
        }
      };
      final SvnVcs vcs = SvnVcs.getInstance(myProject);
      final SVNStatusClient client = vcs.createStatusClient();
      client.setFilesProvider(fileProvider);

      for (VirtualFile parent : myRoots.keySet()) {
        try {
          client.doStatus(new File(parent.getPath()), SVNRevision.WORKING, SVNDepth.INFINITY, false, true, true, false, new ISVNStatusHandler() {
            public void handleStatus(final SVNStatus status) throws SVNException {
              final boolean switched = status.isSwitched();
              final boolean externals = SVNStatusType.STATUS_EXTERNAL.equals(status.getContentsStatus());

              if (switched || externals) {
                final RootUrlInfo urlInfo = mySourceItems.get(FileUtil.nameToCompare(status.getFile().getAbsolutePath()));
                if (urlInfo != null) {
                  if (switched) {
                    urlInfo.setType(NestedCopyType.switched);
                  } else {
                    urlInfo.setType(NestedCopyType.external);
                  }
                }
              }
            }
          }, null);
        }
        catch (SVNException e) {
          LOG.debug(e);
        }
      }
    }
  }

  /*private static SVNStatus getExternalItemStatus(final SvnVcs vcs, final File file) {
    final SVNStatusClient statusClient = vcs.createStatusClient();
    try {
      if (file.isDirectory()) {
        return statusClient.doStatus(file, false);
      } else {
        final File parent = file.getParentFile();
        if (parent != null) {
          statusClient.setFilesProvider(new ISVNStatusFileProvider() {
            public Map getChildrenFiles(File parent) {
              return Collections.singletonMap(file.getAbsolutePath(), file);
            }
          });
          final Ref<SVNStatus> refStatus = new Ref<SVNStatus>();
          statusClient.doStatus(parent, SVNRevision.WORKING, SVNDepth.FILES, false, true, false, false, new ISVNStatusHandler() {
            public void handleStatus(final SVNStatus status) throws SVNException {
              if (file.equals(status.getFile())) {
                refStatus.set(status);
              }
            }
          }, null);
          return refStatus.get();
        }
      }
    }
    catch (SVNException e) {
      //
    }
    return null;
  }*/

  private static class NestedGatherer extends AbstractFilterChildren<RootUrlInfo> {
    private final MultiMap<VirtualFile, RootUrlInfo> myNested;

    private NestedGatherer() {
      myNested = new MultiMap<VirtualFile, RootUrlInfo>();
    }

    @Override
    protected void sortAscending(List<RootUrlInfo> rootUrlInfos) {
      Collections.sort(rootUrlInfos, new Comparator<RootUrlInfo>() {
        public int compare(final RootUrlInfo r1, final RootUrlInfo r2) {
          return new Integer(r1.getVirtualFile().getPath().length()).compareTo(r2.getVirtualFile().getPath().length());
        }
      });
    }

    @Override
    protected boolean isAncestor(RootUrlInfo parent, RootUrlInfo child) {
      final boolean result = VfsUtil.isAncestor(parent.getVirtualFile(), child.getVirtualFile(), true);
      if (result) {
        myNested.putValue(parent.getVirtualFile(), child);
      }
      return result;
    }

    public MultiMap<VirtualFile, RootUrlInfo> getNested() {
      return myNested;
    }
  }
}
