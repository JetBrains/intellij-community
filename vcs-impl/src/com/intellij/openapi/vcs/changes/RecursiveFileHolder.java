/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
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
public class RecursiveFileHolder implements FileHolder {
  protected final HolderType myHolderType;
  protected final Map<VirtualFile, String> myFiles = new HashMap<VirtualFile, String>();
  protected List<VirtualFile> mySwitchRoots;
  protected final Project myProject;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myProject = project;
    myHolderType = holderType;
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
        synchronized(RecursiveFileHolder.this) {
          if (myProject.isDisposed()) return;
          final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles.keySet());
          for (VirtualFile file : currentFiles) {
            if (isFileDirty(scope, file)) {
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

  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    return fileDropped(file) || scope.belongsTo(new FilePathImpl(file));
  }

  public HolderType getType() {
    return myHolderType;
  }

  protected boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null;
  }

  public synchronized void addFile(VirtualFile file, @NotNull String branchName, final boolean recursive) {
    myFiles.put(file, branchName);
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
  }

  public synchronized void removeFile(VirtualFile file) {
    myFiles.remove(file);
    if (mySwitchRoots != null) {
      mySwitchRoots.remove(file);
    }
  }

  public synchronized RecursiveFileHolder copy() {
    final RecursiveFileHolder copyHolder = new RecursiveFileHolder(myProject, myHolderType);
    copyHolder.myFiles.putAll(myFiles);
    return copyHolder;
  }

  public synchronized boolean containsFile(final VirtualFile file) {
    return getBranchForFile(file) != null;
  }

  public synchronized MultiMap<String, VirtualFile> getBranchToFileMap() {
    MultiMap<String, VirtualFile> result = new MultiMap<String, VirtualFile>();
    for(Map.Entry<VirtualFile, String> e: myFiles.entrySet()) {
      result.putValue(e.getValue(), e.getKey());
    }
    return result;
  }

  public String getBranchForFile(final VirtualFile file) {
    for (VirtualFile vf : myFiles.keySet()) {
      if (VfsUtil.isAncestor(vf, file, false)) {
        return myFiles.get(vf);
      }
    }
    return null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFileHolder that = (RecursiveFileHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
