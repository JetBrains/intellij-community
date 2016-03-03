package com.jetbrains.edu.coursecreator;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;

public abstract class CCRenameHandler implements RenameHandler {
  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null || !(element instanceof PsiDirectory)) {
      return false;
    }
    CCProjectService instance = CCProjectService.getInstance(element.getProject());
    Course course = instance.getCourse();
    if (course == null) {
      return false;
    }
    VirtualFile directory = ((PsiDirectory)element).getVirtualFile();
    return isAvailable(directory.getName());
  }

  protected abstract boolean isAvailable(String name);

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    assert element != null;
    PsiDirectory directory = (PsiDirectory)element;
    CCProjectService instance = CCProjectService.getInstance(project);
    Course course = instance.getCourse();
    rename(project, course, directory);
    ProjectView.getInstance(project).refresh();
  }

  protected abstract void rename(@NotNull Project project, @NotNull Course course, @NotNull PsiDirectory directory);


  protected static void processRename(@NotNull final StudyItem item, String namePrefix, @NotNull final Project project) {
    String name = item.getName();
    String text = "Rename " + StringUtil.toTitleCase(namePrefix);
    String newName = Messages.showInputDialog(project, text + " '" + name + "' to", text, null, name, null);
    if (newName != null) {
      item.setName(newName);
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
}
