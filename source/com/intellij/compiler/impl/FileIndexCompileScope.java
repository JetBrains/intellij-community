package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 18, 2003
 */
public abstract class FileIndexCompileScope implements CompileScope {

  protected abstract FileIndex[] getFileIndices();

  public VirtualFile[] getFiles(final FileType fileType, final boolean inSourceOnly) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final FileIndex[] fileIndices = getFileIndices();
        for (int idx = 0; idx < fileIndices.length; idx++) {
          final FileIndex fileIndex = fileIndices[idx];
          fileIndex.iterateContent(new CompilerContentIterator(fileType, fileIndex, inSourceOnly, files));
        }
      }
    });
    return files.toArray(new VirtualFile[files.size()]);
  }
}
