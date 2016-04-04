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
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyItem;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public class CCRenameHandler implements RenameHandler {
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
    String name = directory.getName();
    return name.contains(EduNames.LESSON) || name.contains(EduNames.TASK);
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    assert element != null;
    PsiDirectory directory = (PsiDirectory)element;
    String name = directory.getName();
    CCProjectService instance = CCProjectService.getInstance(project);
    Course course = instance.getCourse();
    if (name.contains(EduNames.LESSON)) {
      renameLesson(project, course, name);
    }
    if (name.contains(EduNames.TASK)) {
      renameTask(project, course, directory);
    }
    ProjectView.getInstance(project).refresh();
  }


  private static void processRename(@NotNull final StudyItem item, String namePrefix, @NotNull final Project project) {
    String name = item.getName();
    String text = "Rename " + StringUtil.toTitleCase(namePrefix);
    String newName = Messages.showInputDialog(project, text + " '" + name + "' to", text, null, name, null);
    if (newName != null) {
      item.setName(newName);
    }
  }

  private static void renameTask(@NotNull final Project project, Course course, @NotNull final PsiDirectory directory) {
    PsiDirectory lessonDir = directory.getParent();
    if (lessonDir == null || !lessonDir.getName().contains(EduNames.LESSON)) {
      return;
    }
    Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return;
    }
    String directoryName = directory.getName();
    Task task = lesson.getTask(directoryName);
    if (task != null) {
      processRename(task, EduNames.TASK, project);
    }
  }

  private static void renameLesson(@NotNull final Project project, Course course, String name) {
    Lesson lesson = course.getLesson(name);
    if (lesson != null) {
      processRename(lesson, EduNames.LESSON, project);
    }
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invoke(project, null, null, dataContext);
  }
}
