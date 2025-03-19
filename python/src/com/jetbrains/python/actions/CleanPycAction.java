// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class CleanPycAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final PsiElement[] elements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (elements == null) return;
    final List<File> pycFiles = new ArrayList<>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      for (PsiElement element : elements) {
        PsiDirectory dir = (PsiDirectory) element;
        collectPycFiles(new File(dir.getVirtualFile().getPath()), pycFiles);
      }
      FileUtil.asyncDelete(pycFiles);
    }, PyBundle.message("action.CleanPyc.progress.title.cleaning.up.pyc.files"), false, e.getProject());
    final StatusBar statusBar = WindowManager.getInstance().getIdeFrame(e.getProject()).getStatusBar();
    statusBar.setInfo(PyBundle.message("action.CleanPyc.status.bar.text.deleted.bytecode.files", pycFiles.size()));
  }

  private static void collectPycFiles(File directory, final List<File> pycFiles) {
    FileUtil.processFilesRecursively(directory, file -> {
      if (file.getParentFile().getName().equals(PyNames.PYCACHE) ||
          FileUtilRt.extensionEquals(file.getName(), "pyc") ||
          FileUtilRt.extensionEquals(file.getName(), "pyo") ||
          file.getName().endsWith("$py.class")) {
        pycFiles.add(file);
      }
      return true;
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final PsiElement[] elements = e.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
    if (e.isFromContextMenu()) {
      e.getPresentation().setEnabledAndVisible(isAllDirectories(elements));
    }
    else {
      e.getPresentation().setEnabled(isAllDirectories(elements));
    }
  }

  private static boolean isAllDirectories(PsiElement @Nullable [] elements) {
    if (elements == null || elements.length == 0) return false;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory) || FileIndexFacade.getInstance(element.getProject())
        .isInLibraryClasses(((PsiDirectory)element).getVirtualFile())) {
        return false;
      }
    }
    return true;
  }
}
