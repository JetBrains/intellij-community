// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.eel.fs.EelFileUtils;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class CleanPycAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var elements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (!isAllDirectories(elements)) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        for (PsiElement element : elements) {
          var dir = Path.of(((PsiDirectory)element).getVirtualFile().getName());
          if (!Files.isDirectory(dir)) continue;
          Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
              if (PyNames.PYCACHE.equals(NioFiles.getFileName(dir))) {
                EelFileUtils.deleteRecursively(dir);
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
              var name = NioFiles.getFileName(file);
              if (name.endsWith(".pyc") || name.endsWith(".pyo") || name.endsWith("$py.class")) {
                Files.deleteIfExists(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
        }
      }
      catch (IOException ex) {
        Logger.getInstance(CleanPycAction.class).warn(ex);
      }
    }, PyBundle.message("action.CleanPyc.progress.title.cleaning.up.pyc.files"), false, e.getProject());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var elements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (e.isFromContextMenu()) {
      e.getPresentation().setEnabledAndVisible(isAllDirectories(elements));
    }
    else {
      e.getPresentation().setEnabled(isAllDirectories(elements));
    }
  }

  private static boolean isAllDirectories(PsiElement @Nullable [] elements) {
    return elements != null && elements.length > 0 && ContainerUtil.all(elements, element ->
      element instanceof PsiDirectory pd &&
      !FileIndexFacade.getInstance(pd.getProject()).isInLibraryClasses(pd.getVirtualFile())
    );
  }
}
