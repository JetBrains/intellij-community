// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FileFinder {
  public static List<VirtualFile> findPomFiles(VirtualFile[] roots,
                                               boolean lookForNested,
                                               @NotNull MavenProgressIndicator indicator,
                                               @NotNull List<VirtualFile> result) throws MavenProcessCanceledException {
    // TODO locate pom files using maven embedder?
    for (VirtualFile f : roots) {
      VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile f) {
          try {
            indicator.checkCanceled();
            indicator.setText2(f.getPath());

            if (f.isDirectory()) {
              if (lookForNested) {
                f.refresh(false, false);
              }
              else {
                return false;
              }
            }
            else {
              if (MavenUtil.isPomFile(f)) {
                result.add(f);
              }
            }
          }
          catch (InvalidVirtualFileAccessException e) {
            // we are accessing VFS without read action here so such exception may occasionally occur
            MavenLog.LOG.info(e);
          }
          catch (MavenProcessCanceledException e) {
            throw new VisitorException(e);
          }
          return true;
        }
      }, MavenProcessCanceledException.class);
    }

    return result;
  }
}
