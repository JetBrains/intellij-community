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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SvnEntriesFileListener extends VirtualFileAdapter {
  private final Project myProject;
  private final Collection<SvnEntriesListener> myListeners = new ArrayList<SvnEntriesListener>();
  private final ProjectLevelVcsManager myVcsManager;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnEntriesFileListener");
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myDirtyScopeManager;

  public SvnEntriesFileListener(final Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  public void fileCreated(VirtualFileEvent event) {
    if (! event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();

    if (SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        fileEntriesChanged(parent);
      }
      return;
    }

    final AbstractVcs vcsFor = myVcsManager.getVcsFor(file);
    if (vcsFor == null) return;
    if (SvnVcs.VCS_NAME.equals(vcsFor.getName())) {
      final RootUrlInfo path = ((SvnVcs)vcsFor).getSvnFileUrlMapping().getWcRootForFilePath(new File(file.getPath()));
      if (path != null && WorkingCopyFormat.ONE_DOT_SEVEN.equals(path.getFormat())) {
        VcsDirtyScopeManager.getInstance(myProject).filesDirty(Collections.singletonList(file), null);
      }
    }
  }

  public void contentsChanged(VirtualFileEvent event) {
    if (! event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (isWcDbFile(file)) {
      LOG.debug("wc.db had changed");
      if (file.getParent() != null && file.getParent().getParent() != null) {
        myDirtyScopeManager.dirDirtyRecursively(file.getParent().getParent());
      }
      return;
    }
    if (isEntriesFile(file) && file.getParent() != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        VirtualFile grandParent = parent.getParent();
        if (grandParent != null) {
          fireFileStatusesChanged(grandParent);
          fileEntriesChanged(grandParent);
        }
      }
      return;
    }

    final AbstractVcs vcsFor = myVcsManager.getVcsFor(file);
    if (vcsFor == null) return;
    if (SvnVcs.VCS_NAME.equals(vcsFor.getName())) {
      final RootUrlInfo path = ((SvnVcs)vcsFor).getSvnFileUrlMapping().getWcRootForFilePath(new File(file.getPath()));
      if (path != null && WorkingCopyFormat.ONE_DOT_SEVEN.equals(path.getFormat())) {
        VcsDirtyScopeManager.getInstance(myProject).filesDirty(Collections.singletonList(file), null);
        fileRevisionProbablyChanged(file);
      }
    }
  }

  public void fileDeleted(VirtualFileEvent event) {
    if (!event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        fileEntriesChanged(parent);
      }
      return;
    }

    if (event.getParent() != null) {
      final VirtualFile parent = event.getParent();
      final AbstractVcs vcsFor = myVcsManager.getVcsFor(parent);
      if (vcsFor == null) return;
      if (SvnVcs.VCS_NAME.equals(vcsFor.getName())) {
        final RootUrlInfo path = ((SvnVcs)vcsFor).getSvnFileUrlMapping().getWcRootForFilePath(new File(parent.getPath()));
        if (path != null && WorkingCopyFormat.ONE_DOT_SEVEN.equals(path.getFormat())) {
          myDirtyScopeManager.filePathsDirty(
            Collections.<FilePath>singletonList(new FilePathImpl(new File(file.getPath()), file.isDirectory())), null);
        }
      }
    }
  }

  public void fileRevisionProbablyChanged(final VirtualFile file) {
    final SvnEntriesListener[] listeners = myListeners.toArray(new SvnEntriesListener[myListeners.size()]);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        for (SvnEntriesListener listener : listeners) {
          listener.fileVersionProbablyChanged(file);
        }
      }
    });
  }

  private void fileEntriesChanged(final VirtualFile parent) {
    final SvnEntriesListener[] listeners = myListeners.toArray(new SvnEntriesListener[myListeners.size()]);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        for (SvnEntriesListener listener : listeners) {
          listener.onEntriesChanged(parent);
        }
      }
    });
  }

  public void addListener(SvnEntriesListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(SvnEntriesListener listener) {
    myListeners.remove(listener);
  }

  private void fireFileStatusesChanged(VirtualFile parent) {
    final VirtualFile[] children = parent.getChildren();
    final List<VirtualFile> files = new ArrayList<VirtualFile>(children.length + 1);
    files.add(parent);
    Collections.addAll(files, children);
    myDirtyScopeManager.filesDirty(files, null);
    /*
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
    final VirtualFile[] children = parent.getChildren();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        for (int i = 0; i < children.length; i++) {
          VirtualFile child = children[i];
          fileStatusManager.fileStatusChanged(child);
        }
      }
    }, ModalityState.NON_MMODAL);
    */
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
