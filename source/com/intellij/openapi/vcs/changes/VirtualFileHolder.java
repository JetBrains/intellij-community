package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FilePathImpl;
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

  public synchronized void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles);
        for (VirtualFile file : currentFiles) {
          if (fileDropped(file) || scope.belongsTo(new FilePathImpl(file))) {
            myFiles.remove(file);
          }
        }
      }
    });
  }

  private boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file);
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
}
