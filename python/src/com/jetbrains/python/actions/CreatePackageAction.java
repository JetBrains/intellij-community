package com.jetbrains.python.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageHandler;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.PyNames;

/**
 * @author yole
 */
public class CreatePackageAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    new WriteCommandAction.Simple(project, "Create Package") {
      @Override
      protected void run() throws Throwable {
        CreateDirectoryOrPackageHandler validator = new CreateDirectoryOrPackageHandler(project, directory, false, ".");
        Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.package.name"),
                                          IdeBundle.message("title.new.package"),
                                          Messages.getQuestionIcon(), "", validator);
        final PsiDirectory result = validator.getCreatedElement();
        if (result != null && view != null) {
          createInitPyInHierarchy(result, directory);
          view.selectElement(result);
        }
      }
    }.execute();
  }

  private static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
    do {
      createInitPy(created);
      created = created.getParent();
    } while(created != null && created != ancestor);
  }

  private static void createInitPy(PsiDirectory directory) {
    final PsiFile file = PsiFileFactory.getInstance(directory.getProject()).createFileFromText(PyNames.INIT_DOT_PY, "");
    directory.add(file);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final IdeView ideView = e.getData(LangDataKeys.IDE_VIEW);
    if (project == null || ideView == null) {
      return false;
    }
    final PsiDirectory[] directories = ideView.getDirectories();
    if (directories.length == 0) {
      return false;
    }
    for (PsiDirectory directory : directories) {
      if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
        return true;
      }
    }
    return false;
  }
}
