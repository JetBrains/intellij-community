package com.jetbrains.python.refactoring.packages;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyConvertPackageToModuleAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(PyConvertPackageToModuleAction.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PsiDirectory element = getPackageDir(e);
    assert element != null;
    final Project project = e.getProject();
    if (project != null) {
      createModuleFromPackage(element, project);
    }
  }

  @VisibleForTesting
  public static void createModuleFromPackage(@NotNull final PsiDirectory pyPackage, @NotNull final Project project) {
    if (pyPackage.getParent() == null) {
      return;
    }

    final VirtualFile parentDirVFile = pyPackage.getParent().getVirtualFile();
    final PsiFile initPy = pyPackage.findFile(PyNames.INIT_DOT_PY);
    final String moduleName = pyPackage.getName() + PyNames.DOT_PY;
    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      public void run() {
        try {
          if (initPy != null) {
            final VirtualFile initPyVFile = initPy.getVirtualFile();
            initPyVFile.rename(this, moduleName);
            initPyVFile.move(this, parentDirVFile);
          }
          else {
            PyUtil.getOrCreateFile(parentDirVFile.getPath() + "/" + moduleName, project);
          }
          pyPackage.getVirtualFile().delete(this);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean enabled = isEnabled(e);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    final PsiFileSystemItem packageElement = getPackageDir(e);
    return packageElement != null && isStrictlyUnderContentRoots(packageElement);
  }

  @Nullable
  private static PsiDirectory getPackageDir(@NotNull AnActionEvent e) {
    PsiDirectory result = null;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile vFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project != null && vFile != null) {
      final PsiManager psiManager = PsiManager.getInstance(project);
      if (vFile.isDirectory()) {
        final PsiDirectory dir = psiManager.findDirectory(vFile);
        if (dir != null && PyUtil.isPackage(dir, null)) {
          result = dir;
        }
      }
      else {
        final PsiFile file = psiManager.findFile(vFile);
        if (file != null && PyUtil.isPackage(file)) {
          result = file.getParent();
        }
      }
    }
    if (result != null && isStrictlyUnderContentRoots(result) && isEmptyPackage(result)) {
      return result;
    }
    return null;
  }

  private static boolean isStrictlyUnderContentRoots(@NotNull PsiFileSystemItem element) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return false;
    }
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return !ArrayUtil.contains(element.getVirtualFile(), contentRoots);
  }

  private static boolean isEmptyPackage(@NotNull PsiDirectory pyPackage) {
    final PsiElement[] children = pyPackage.getChildren();
    if (children.length == 1) {
      final PyFile onlyFile = as(children[0], PyFile.class);
      return onlyFile != null && onlyFile.getName().equals(PyNames.INIT_DOT_PY);
    }
    return children.length == 0;
  }
}
