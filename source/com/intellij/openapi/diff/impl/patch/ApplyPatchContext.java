/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ApplyPatchContext {
  private VirtualFile myBaseDir;
  private int mySkipTopDirs;
  private boolean myCreateDirectories;
  private boolean myAllowRename;

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
}