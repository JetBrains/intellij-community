/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class ApplyPatchContext {
  private final VirtualFile myBaseDir;
  private final int mySkipTopDirs;
  private final boolean myCreateDirectories;
  private final boolean myAllowRename;
  private Map<VirtualFile, String> myPendingRenames = null;
  private TreeSet<String> myMissingDirectories = new TreeSet<String>();
  
  public ApplyPatchContext(final VirtualFile baseDir, final int skipTopDirs, final boolean createDirectories, final boolean allowRename) {
    myBaseDir = baseDir;
    mySkipTopDirs = skipTopDirs;
    myCreateDirectories = createDirectories;
    myAllowRename = allowRename;
  }

  public VirtualFile getBaseDir() {
    return myBaseDir;
  }

  public int getSkipTopDirs() {
    return mySkipTopDirs;
  }

  public boolean isAllowRename() {
    return myAllowRename;
  }

  public boolean isCreateDirectories() {
    return myCreateDirectories;
  }

  public ApplyPatchContext getPrepareContext() {
    return new ApplyPatchContext(myBaseDir, mySkipTopDirs, false, false);
  }

  public void addPendingRename(VirtualFile file, String newName) {
    if (myPendingRenames == null) {
      myPendingRenames = new HashMap<VirtualFile, String>();
    }
    myPendingRenames.put(file, newName);
  }

  public void applyPendingRenames() throws IOException {
    if (myPendingRenames != null) {
      for(Map.Entry<VirtualFile, String> entry: myPendingRenames.entrySet()) {
        entry.getKey().rename(FilePatch.class, entry.getValue());      
      }
      myPendingRenames = null;
    }
  }

  public void registerMissingDirectory(final VirtualFile existingDir, final String[] pathNameComponents, final int firstMissingIndex) {
    String path = existingDir.getPath();
    for(int i=firstMissingIndex; i<pathNameComponents.length-1; i++) {
      path += "/" + pathNameComponents [i];
      myMissingDirectories.add(FileUtil.toSystemDependentName(path));
    }
  }

  public Collection<String> getMissingDirectories() {
    return Collections.unmodifiableSet(myMissingDirectories);
  }
}