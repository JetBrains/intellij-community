package com.intellij.ide.actions;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class CreateFileAction extends CreateElementActionBase {
  public CreateFileAction() {
    super("Create New File", "Create New File", Icons.CUSTOM_FILE_ICON);
  }

  protected PsiElement[] invokeDialog(final Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new MyValidator(project, directory);
    Messages.showInputDialog(project, "Enter a new file name:", "New File", Messages.getQuestionIcon(), null, validator);
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateFile(newName);
  }

  protected PsiElement[] create(String newName, PsiDirectory directory) throws IncorrectOperationException {
    return new PsiElement[]{directory.createFile(newName)};
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating file " + directory.getVirtualFile().getPresentableUrl() + File.separator + newName;
  }

  protected String getErrorTitle() {
    return "Cannot Create File";
  }

  protected String getCommandName() {
    return "Create file";
  }

  private class MyValidator extends CreateElementActionBase.MyInputValidator {
    private final Project myProject;

    public boolean checkInput(String inputString) {
      return true;
    }

    public boolean canClose(String inputString) {
      String fileName = inputString;

      if (fileName.length() == 0) {
        return super.canClose(inputString);
      }
//     27.05.03 [dyoma] investigating the reason...
//      FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
//      if (fileTypeManager.getExtension(fileName).length()==0) {
//        Messages.showMessageDialog(myProject, "Cannot create file with no extension", "Error", Messages.getErrorIcon());
//        return false;
//      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(fileName);
      if (type == null) return false;

      return super.canClose(inputString);
    }

    public MyValidator(Project project, PsiDirectory directory){
      super(project, directory);
      myProject = project;
    }
  }
}
