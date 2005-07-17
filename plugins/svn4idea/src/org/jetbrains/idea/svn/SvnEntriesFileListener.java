package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import java.util.Collection;
import java.util.ArrayList;

public class SvnEntriesFileListener extends VirtualFileAdapter {
  private final Project myProject;
  private final Collection<SvnEntriesListener> myListeners = new ArrayList<SvnEntriesListener>();

  public SvnEntriesFileListener(final Project project) {
    myProject = project;
  }

  public void fileCreated(VirtualFileEvent event) {
    if (event.getRequestor() != null) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (file != null && ".svn".equals(file.getName())) {
      if (event.getParent() != null) {
        VirtualFile parent = event.getParent();
        fireFileStatusesChanged(parent);
        fileEntriesChanged(parent);
      }
    }
  }

  public void contentsChanged(VirtualFileEvent event) {
    if (event.getRequestor() != null) {
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
    if (event.getRequestor() != null) {
      return;
    }
    final VirtualFile file = event.getFile();
    if (file != null && ".svn".equals(file.getName())) {
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
    }, ModalityState.NON_MMODAL);
  }

  public void addListener(SvnEntriesListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(SvnEntriesListener listener) {
    myListeners.remove(listener);
  }

  private void fireFileStatusesChanged(VirtualFile parent) {
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
  }

  private static boolean isEntriesFile(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return !file.isDirectory() && "entries".equals(file.getName()) && parent != null && ".svn".equals(parent.getName());
  }
}
