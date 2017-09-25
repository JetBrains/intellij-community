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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SvnEntriesFileListener implements VirtualFileListener {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnEntriesFileListener");
  private VcsDirtyScopeManager myDirtyScopeManager;

  public SvnEntriesFileListener(final Project project) {
    myProject = project;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    if (! event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();

    if (SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        refreshAnnotationsUnder(parent);
      }
    }
  }

  private void refreshAnnotationsUnder(VirtualFile parent) {
    BackgroundTaskUtil.syncPublisher(myProject, VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirtyUnder(parent);
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    if (! event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (isWcDbFile(file) && SvnVcs.ourListenToWcDb) {
      LOG.debug("wc.db had changed");
      final VirtualFile parentWcDb = file.getParent();
      if (parentWcDb != null && SvnUtil.isAdminDirectory(parentWcDb)) {
        final VirtualFile parent = parentWcDb.getParent();
        if (parent != null) {
          myDirtyScopeManager.dirDirtyRecursively(parent);
          refreshAnnotationsUnder(parent);
        }
      }
      return;
    }
    if (isEntriesFile(file) && file.getParent() != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        VirtualFile grandParent = parent.getParent();
        if (grandParent != null) {
          fireFileStatusesChanged(grandParent);
          refreshAnnotationsUnder(grandParent);
        }
      }
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    if (!event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        refreshAnnotationsUnder(parent);
      }
      return;
    }
  }

  private void fireFileStatusesChanged(VirtualFile parent) {
    final VirtualFile[] children = parent.getChildren();
    final List<VirtualFile> files = new ArrayList<>(children.length + 1);
    files.add(parent);
    Collections.addAll(files, children);
    myDirtyScopeManager.filesDirty(files, null);
  }

  private static boolean isEntriesFile(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return ! file.isDirectory() && SvnUtil.ENTRIES_FILE_NAME.equals(file.getName()) && parent != null && SvnUtil.SVN_ADMIN_DIR_NAME.equals(parent.getName());
  }

  private static boolean isWcDbFile(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return ! file.isDirectory() && SvnUtil.WC_DB_FILE_NAME.equals(file.getName()) && parent != null && SvnUtil.SVN_ADMIN_DIR_NAME.equals(parent.getName());
  }
}
