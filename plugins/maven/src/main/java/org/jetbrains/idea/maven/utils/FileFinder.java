/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FileFinder {
  public static List<VirtualFile> findPomFiles(VirtualFile[] roots,
                                               final boolean lookForNested,
                                               final MavenProgressIndicator indicator,
                                               final List<VirtualFile> result) throws MavenProcessCanceledException {

    // TODO locate pom files using maven embedder?

    for (VirtualFile f : roots) {
      VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor() {
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
