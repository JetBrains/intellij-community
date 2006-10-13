/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;

import java.util.ArrayList;
import java.util.Collection;

public class SvnEntriesFileListener extends VirtualFileAdapter {
  private final Project myProject;
  private final Collection<SvnEntriesListener> myListeners = new ArrayList<SvnEntriesListener>();

  public SvnEntriesFileListener(final Project project) {
    myProject = project;
  }

  public void fileCreated(VirtualFileEvent event) {
    if (!event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (file != null && SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        fileEntriesChanged(parent);
      }
    }
  }

  public void contentsChanged(VirtualFileEvent event) {
    if (!event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (file != null && isEntriesFile(file) && file.getParent() != null) {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        VirtualFile grandParent = parent.getParent();
        if (grandParent != null) {
          fireFileStatusesChanged(grandParent);
          fileEntriesChanged(grandParent);
        }
      }
    }
  }

  public void fileDeleted(VirtualFileEvent event) {
    if (!event.isFromRefresh()) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (file != null && SvnUtil.SVN_ADMIN_DIR_NAME.equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        fileEntriesChanged(parent);
      }
    }
  }

  private void fileEntriesChanged(final VirtualFile parent) {
    final SvnEntriesListener[] listeners = myListeners.toArray(new SvnEntriesListener[myListeners.size()]);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        for (int i = 0; i < listeners.length; i++) {
          SvnEntriesListener listener = listeners[i];
          listener.onEntriesChanged(parent);
        }
      }
    }, ModalityState.NON_MODAL);
  }

  public void addListener(SvnEntriesListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(SvnEntriesListener listener) {
    myListeners.remove(listener);
  }

  private void fireFileStatusesChanged(VirtualFile parent) {
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(parent);
    final VirtualFile[] children = parent.getChildren();
    for(int i = 0; i < children.length; i++) {
      VcsDirtyScopeManager.getInstance(myProject).fileDirty(children[i]);    
    }
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
    return !file.isDirectory() && SvnUtil.ENTRIES_FILE_NAME.equals(file.getName()) && parent != null && SvnUtil.SVN_ADMIN_DIR_NAME.equals(parent.getName());
  }
}
