package com.intellij.ide.actions;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ide.IdeBundle;

import java.io.File;

public class CreateFileAction extends CreateElementActionBase {
  public CreateFileAction() {
    super(IdeBundle.message("action.create.new.file"), IdeBundle.message("action.create.new.file"), IconLoader.getIcon("/fileTypes/text.png"));
  }

  protected PsiElement[] invokeDialog(final Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new MyValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.file.name"),
                             IdeBundle.message("title.new.file"), Messages.getQuestionIcon(), null, validator);
    return validator.getCreatedElements();
  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    directory.checkCreateFile(newName);
  }

  protected PsiElement[] create(String newName, PsiDirectory directory) throws IncorrectOperationException {
    return new PsiElement[]{directory.createFile(newName)};
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.file", directory.getVirtualFile().getPresentableUrl(), File.separator, newName);
  }

  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.file");
  }

  protected String getCommandName() {
    return IdeBundle.message("command.create.file");
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
