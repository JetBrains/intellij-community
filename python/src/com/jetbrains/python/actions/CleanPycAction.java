/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
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

/**
 * @author yole
 */
public class CleanPycAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
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
  public void update(@NotNull AnActionEvent e) {
    final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
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
