package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class VirtualFileHolder {
  private Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
  private Project myProject;

  public VirtualFileHolder(Project project) {
    myProject = project;
  }

  public synchronized void cleanAll() {
    myFiles.clear();
  }

  public void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        // to avoid deadlocks caused by incorrect lock ordering, need to lock on this after taking read action
        synchronized(VirtualFileHolder.this) {
          if (myProject.isDisposed() || myFiles.isEmpty()) return;
          final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles);
          if (scope.getRecursivelyDirtyDirectories().size() == 0) {
            final Set<FilePath> dirtyFiles = scope.getDirtyFiles();
            for(FilePath dirtyFile: dirtyFiles) {
              VirtualFile f = dirtyFile.getVirtualFile();
              if (f != null) {
                myFiles.remove(f);
              }
            }
          }
          else {
            for (VirtualFile file : currentFiles) {
              if (fileDropped(file) || scope.belongsTo(new FilePathImpl(file))) {
                myFiles.remove(file);
              }
            }
          }
        }
      }
    });
  }

  private boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null;
  }

  public synchronized void addFile(VirtualFile file) {
    myFiles.add(file);
  }

  public synchronized void removeFile(VirtualFile file) {
    myFiles.remove(file);
  }

  public synchronized List<VirtualFile> getFiles() {
    return new ArrayList<VirtualFile>(myFiles);
  }

  public synchronized VirtualFileHolder copy() {
    final VirtualFileHolder copyHolder = new VirtualFileHolder(myProject);
    copyHolder.myFiles.addAll(myFiles);
    return copyHolder;
  }

  public synchronized boolean containsFile(final VirtualFile file) {
    return myFiles.contains(file);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VirtualFileHolder that = (VirtualFileHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
