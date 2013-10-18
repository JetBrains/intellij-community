package com.jetbrains.python.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CleanPycAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    if (elements == null) return;
    final List<File> pycFiles = new ArrayList<File>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        for (PsiElement element : elements) {
          PsiDirectory dir = (PsiDirectory) element;
          collectPycFiles(new File(dir.getVirtualFile().getPath()), pycFiles);
        }
        FileUtil.asyncDelete(pycFiles);
      }
    }, "Cleaning up .py files...", false, e.getProject());
    final StatusBar statusBar = WindowManager.getInstance().getIdeFrame(e.getProject()).getStatusBar();
    statusBar.setInfo("Deleted " + pycFiles.size() + " bytecode file" + (pycFiles.size() > 1 ? "s" : ""));
  }

  private static void collectPycFiles(File directory, final List<File> pycFiles) {
    FileUtil.processFilesRecursively(directory, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.getParentFile().getName().equals(PyNames.PYCACHE) ||
            FileUtilRt.extensionEquals(file.getName(), "pyc") ||
            FileUtilRt.extensionEquals(file.getName(), "pyo")) {
          pycFiles.add(file);
        }
        return true;
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    final PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
    e.getPresentation().setEnabled(isAllDirectories(elements));
  }

  private static boolean isAllDirectories(@Nullable PsiElement[] elements) {
    if (elements == null || elements.length == 0) return false;
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }
    return true;
  }
}
