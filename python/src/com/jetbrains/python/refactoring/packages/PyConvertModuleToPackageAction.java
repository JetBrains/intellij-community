package com.jetbrains.python.refactoring.packages;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
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
public class PyConvertModuleToPackageAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(PyConvertModuleToPackageAction.class);

  @Override
  public void actionPerformed(AnActionEvent e) {
    final PyFile pyFile = getPythonFile(e);
    assert pyFile != null;
    final Project project = e.getProject();
    if (project != null) {
      createPackageFromModule(pyFile, project);
    }
  }

  @VisibleForTesting
  public static void createPackageFromModule(@NotNull final PyFile file, @NotNull Project project) {
    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      public void run() {
        final VirtualFile vFile = file.getVirtualFile();
        final VirtualFile parentDir = vFile.getParent();
        try {
          final VirtualFile packageDir = parentDir.createChildDirectory(this, vFile.getNameWithoutExtension());
          vFile.move(this, packageDir);
          vFile.rename(this, PyNames.INIT_DOT_PY);
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

  @Nullable
  private static PyFile getPythonFile(@NotNull AnActionEvent e) {
    final VirtualFile vFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null && vFile != null && !vFile.isDirectory()) {
      final PsiManager psiManager = PsiManager.getInstance(project);
      return as(psiManager.findFile(vFile), PyFile.class);
    }
    return null;
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    final PyFile file = getPythonFile(e);
    return file != null && !PyUtil.isPackage(file);
  }
}
