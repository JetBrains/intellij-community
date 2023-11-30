// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;

public final class FileFinder {
  public static List<VirtualFile> findPomFiles(VirtualFile[] roots,
                                               boolean lookForNested,
                                               @NotNull MavenProgressIndicator indicator) throws MavenProcessCanceledException {
    return findPomFiles(roots, lookForNested, indicator.getIndicator());
  }

  public static List<VirtualFile> findPomFiles(VirtualFile[] roots,
                                               boolean lookForNested,
                                               @Nullable ProgressIndicator indicator) throws MavenProcessCanceledException {
    List<Pair<VirtualFile, VirtualFile>> result = new ArrayList<>();
    // TODO locate pom files using maven embedder?
    for (VirtualFile f : roots) {
      VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile f) {
          try {
            if (null != indicator) {
              indicator.checkCanceled();
              indicator.setText2(f.getPresentableUrl());
            }

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
                result.add(Pair.create(f.getParent(), f));
              }
            }
          }
          catch (InvalidVirtualFileAccessException e) {
            // we are accessing VFS without read action here so such exception may occasionally occur
            MavenLog.LOG.info(e);
          }
          return true;
        }
      }, MavenProcessCanceledException.class);
    }
    Map<VirtualFile, List<VirtualFile>> pomFilesByParent = result.stream()
      .collect(groupingBy(p -> p.getFirst(), mapping(p -> p.getSecond(), toList())));
    return pomFilesByParent.entrySet().stream()
      .flatMap(pomsByParent -> getOriginalPoms(pomsByParent.getValue()).stream())
      .collect(toList());
  }

  private static List<VirtualFile> getOriginalPoms(@NotNull List<VirtualFile> pomFiles) {
    if (pomFiles.size() < 2) return pomFiles;

    List<VirtualFile> originalPoms = new ArrayList<>();
    for (VirtualFile file : pomFiles) {
      if (file.getName().equals(MavenConstants.POM_XML)) {
        return Collections.singletonList(file);
      }
      if (MavenUtil.isPomFileName(file.getName())) {
        originalPoms.add(file);
      }
    }
    return originalPoms.isEmpty() ? pomFiles : originalPoms;
  }
}
