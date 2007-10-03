/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class SwitchedFileHolder {
  private Map<VirtualFile, String> myFiles = new HashMap<VirtualFile, String>();
  private Project myProject;

  public SwitchedFileHolder(Project project) {
    myProject = project;
  }

  public synchronized void cleanAll() {
    myFiles.clear();
  }

  public void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        // to avoid deadlocks caused by incorrect lock ordering, need to lock on this after taking read action
        synchronized(SwitchedFileHolder.this) {
          if (myProject.isDisposed()) return;
          final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles.keySet());
          for (VirtualFile file : currentFiles) {
            if (fileDropped(file) || scope.belongsTo(new FilePathImpl(file))) {
              myFiles.remove(file);
            }
          }
        }
      }
    });
  }

  private boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null;
  }

  public synchronized void addFile(VirtualFile file, String branchName, final boolean recursive) {
    myFiles.put(file, branchName);
    if (recursive && file.isDirectory()) {
      for(VirtualFile child: file.getChildren()) {
        if (!FileTypeManager.getInstance().isFileIgnored(child.getName())) {
          addFile(child, branchName, recursive);
        }
      }
    }
  }

  public synchronized void removeFile(VirtualFile file) {
    myFiles.remove(file);
  }

  public synchronized SwitchedFileHolder copy() {
    final SwitchedFileHolder copyHolder = new SwitchedFileHolder(myProject);
    copyHolder.myFiles.putAll(myFiles);
    return copyHolder;
  }

  public synchronized boolean containsFile(final VirtualFile file) {
    return myFiles.containsKey(file);
  }

  public synchronized MultiMap<String, VirtualFile> getBranchToFileMap() {
    MultiMap<String, VirtualFile> result = new MultiMap<String, VirtualFile>();
    for(Map.Entry<VirtualFile, String> e: myFiles.entrySet()) {
      result.putValue(e.getValue(), e.getKey());
    }
    return result;
  }

  public String getBranchForFile(final VirtualFile file) {
    return myFiles.get(file);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SwitchedFileHolder that = (SwitchedFileHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}