package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

public class CreateClassAction extends CreateElementActionBase {
  public CreateClassAction() {
    super("Create New Class", "Create New Class", Icons.CLASS_ICON);
  }

  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new CreateElementActionBase.MyInputValidator(project, directory);
    Messages.showInputDialog(project, "Enter a new class name:", "New Class", Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();
  }

  protected String getCommandName() {
    return "Create class";
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateClass(newName);
  }

  protected String getErrorTitle() {
    return "Cannot Create Class";
  }

  public void update(AnActionEvent e) {
    super.update(e);
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
      ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      PsiDirectory[] dirs = view.getDirectories();
      for (int i = 0; i < dirs.length; i++) {
        PsiDirectory dir = dirs[i];
        if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && dir.getPackage() != null) {
          return;
        }
      }

      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating class " + directory.getPackage().getQualifiedName() + "." + newName;
  }

  protected PsiElement[] create(String newName, PsiDirectory directory) throws IncorrectOperationException {
    return new PsiElement[]{directory.createClass(newName)};
  }
}
