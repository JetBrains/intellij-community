package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 * 
 * @author Eugene Zhuravlev
 *         Date: May 5, 2004
 */
public class TrackDependenciesScope implements CompileScope{
  private final CompileScope myDelegate;

  public TrackDependenciesScope(CompileScope delegate) {
    myDelegate = delegate;
  }

  public VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly) {
    return myDelegate.getFiles(fileType, inSourceOnly);
  }

  public boolean belongs(String url) {
    return myDelegate.belongs(url);
  }

  public Module[] getAffectedModules() {
    return myDelegate.getAffectedModules();
  }
}
