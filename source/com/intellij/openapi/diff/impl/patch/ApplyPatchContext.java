/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

/**
 * @author yole
 */
public class ApplyPatchContext {
  private VirtualFile myBaseDir;
  private int mySkipTopDirs;
  private boolean myCreateDirectories;
  private boolean myAllowRename;
  private Map<VirtualFile, String> myPendingRenames = null;

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
}