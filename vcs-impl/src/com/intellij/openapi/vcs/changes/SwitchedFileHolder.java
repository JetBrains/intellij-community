/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class SwitchedFileHolder {
  private Map<VirtualFile, String> myFiles = new HashMap<VirtualFile, String>();
  private List<VirtualFile> mySwitchRoots;
  private Project myProject;

  public SwitchedFileHolder(Project project) {
    myProject = project;
  }

  public synchronized void cleanAll() {
    myFiles.clear();
    if (mySwitchRoots != null) {
      mySwitchRoots.clear();
    }
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
              if (mySwitchRoots != null) {
                mySwitchRoots.remove(file);
              }
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

  public synchronized void addFile(VirtualFile file, @NotNull String branchName, final boolean recursive) {
    myFiles.put(file, branchName);
  }

  private void goForChildren(final VirtualFile file, final String branchName) {
    if (file.isDirectory()) {
      for(VirtualFile child: file.getChildren()) {
        if (!FileTypeManager.getInstance().isFileIgnored(child.getName())) {
          if (! myFiles.containsKey(child)) {
            myFiles.put(child, branchName);
            goForChildren(child, branchName);
          }
        }
      }
    }
  }

  // remove switched to same branch decsendants
  private void preCheckFiles() {
    // do not take latest as parent
    for (int i = 0; i < (mySwitchRoots.size() - 1); i++) {
      final VirtualFile switchRoot = mySwitchRoots.get(i);
      final String parentUrl = myFiles.get(switchRoot);

      for (Iterator<VirtualFile> iterator = mySwitchRoots.listIterator(i + 1); iterator.hasNext();) {
        final VirtualFile file = iterator.next();
        final String childUrl = myFiles.get(file);

        if (VfsUtil.isAncestor(switchRoot, file, true) && childUrl.startsWith(parentUrl)) {
          if (childUrl.length() > parentUrl.length()) {
            //check paths same
            String subUrl = childUrl.substring(parentUrl.length());
            subUrl = subUrl.startsWith("/") ? subUrl.substring(1) : subUrl;
            String relativePath = VfsUtil.getRelativePath(file, switchRoot, '/');
            if (relativePath != null) {
              relativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
              if (relativePath.equals(subUrl)) {
                iterator.remove();
              }
            }
          } else if (childUrl.length() == parentUrl.length()) {
            iterator.remove();
          }
        }
      }
    }

    // remove from map also
    for (Iterator<Map.Entry<VirtualFile, String>> iterator = myFiles.entrySet().iterator(); iterator.hasNext();) {
      final Map.Entry<VirtualFile, String> entry = iterator.next();
      if (! mySwitchRoots.contains(entry.getKey())) {
        iterator.remove();
      }
    }
  }

  public synchronized void calculateChildren() {
    // recursive is always true, so just go for children
    mySwitchRoots = new ArrayList<VirtualFile>(myFiles.keySet());
    Collections.sort(mySwitchRoots, FilePathComparator.getInstance());

    preCheckFiles();

    Collections.reverse(mySwitchRoots);

    for (VirtualFile switchRoot : mySwitchRoots) {
      goForChildren(switchRoot, myFiles.get(switchRoot));
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