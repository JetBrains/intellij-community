package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludedOutputFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;

/**
 *  @author dsl
 */
public class ExcludedOutputFolderImpl implements ExcludedOutputFolder {
  private final ContentEntryImpl myContentEntry;
  private final VirtualFilePointer myOutputPath;

  ExcludedOutputFolderImpl(ContentEntryImpl contentEntry, VirtualFilePointer outputPath) {
    myContentEntry = contentEntry;
    myOutputPath = outputPath;
  }

  public VirtualFile getFile() {
    final VirtualFile file = myOutputPath.getFile();
    if (file == null || file.isDirectory()) {
      return file;
    }
    else {
      return null;
    }
  }

  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  public String getUrl() {
    return myOutputPath.getUrl();
  }

  public boolean isSynthetic() {
    return true;
  }
}
