package com.jetbrains.edu.coursecreator;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.util.Function;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyItem;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.coursecreator.ui.CCMoveStudyItemDialog;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class CCTaskMoveHandlerDelegate extends MoveHandlerDelegate {

  private static final Logger LOG = Logger.getInstance(CCTaskMoveHandlerDelegate.class);
  @Override
  public boolean canMove(DataContext dataContext) {
    if (CommonDataKeys.PSI_FILE.getData(dataContext) != null) {
      return false;
    }
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return false;
    }
    PsiDirectory sourceDirectory = DirectoryChooserUtil.getOrChooseDirectory(view);
    return isTaskDir(sourceDirectory);
  }

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    if (elements.length > 0 && elements[0] instanceof PsiDirectory) {
      return isTaskDir(((PsiDirectory)elements[0]));
    }
    return false;
  }

  private static boolean isTaskDir(PsiDirectory sourceDirectory) {
    if (sourceDirectory == null) {
      return false;
    }
    CCProjectService service = CCProjectService.getInstance(sourceDirectory.getProject());
    Course course = service.getCourse();
    if (course == null) {
      return false;
    }
    return EduUtils.getTask(sourceDirectory, course) != null;
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    return true;
  }

  @Override
  public void doMove(final Project project,
                     PsiElement[] elements,
                     @Nullable PsiElement targetContainer,
                     @Nullable MoveCallback callback) {
    if (targetContainer == null || !(targetContainer instanceof PsiDirectory)) {
      return;
    }

    PsiDirectory targetDir = (PsiDirectory)targetContainer;
    if (!isTaskDir(targetDir) && !CCUtils.isLessonDir(targetDir)) {
      Messages.showInfoMessage("Tasks can be moved only to other lessons or inside lesson", "Incorrect Target For Move");
      return;
    }
    final Course course = CCProjectService.getInstance(project).getCourse();
    final PsiDirectory sourceDirectory = (PsiDirectory)elements[0];

    final Task taskToMove = EduUtils.getTask(sourceDirectory, course);
    if (taskToMove == null) {
      return;
    }

    if (CCUtils.isLessonDir(targetDir)) {
      //if user moves task to any lesson, this task is inserted as the last task in this lesson
      Lesson targetLesson = course.getLesson(targetDir.getName());
      if (targetLesson == null) {
        return;
      }
      List<Task> taskList = targetLesson.getTaskList();
      moveTask(sourceDirectory, taskToMove, taskList.isEmpty() ? null : taskList.get(taskList.size() - 1),
               1, targetDir.getVirtualFile(), targetLesson);
    }
    else {
      PsiDirectory lessonDir = targetDir.getParent();
      if (lessonDir == null) {
        return;
      }
      Task targetTask = EduUtils.getTask(targetDir, course);
      if (targetTask == null) {
        return;
      }
      final CCMoveStudyItemDialog dialog = new CCMoveStudyItemDialog(project, EduNames.TASK, targetTask.getName());
      dialog.show();
      if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
        return;
      }
      moveTask(sourceDirectory, taskToMove, targetTask, dialog.getIndexDelta(),
               lessonDir.getVirtualFile(), targetTask.getLesson());
    }

  }

  private void moveTask(final PsiDirectory sourceDirectory,
                        final Task taskToMove,
                        Task targetTask,
                        int indexDelta,
                        final VirtualFile targetDirectory,
                        Lesson targetLesson) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          sourceDirectory.getVirtualFile().rename(this, "tmp");
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
    final VirtualFile sourceLessonDir = sourceDirectory.getVirtualFile().getParent();
    if (sourceLessonDir == null) {
      return;
    }
    CCUtils.updateHigherElements(sourceLessonDir.getChildren(), new Function<VirtualFile, StudyItem>() {
      @Override
      public StudyItem fun(VirtualFile file) {
        return taskToMove.getLesson().getTask(file.getName());
      }
    }, taskToMove.getIndex(), EduNames.TASK, -1);

    final int newItemIndex = targetTask != null ? targetTask.getIndex() + indexDelta : 1;
    taskToMove.setIndex(-1);
    taskToMove.getLesson().getTaskList().remove(taskToMove);
    final Lesson finalTargetLesson = targetLesson;
    CCUtils.updateHigherElements(targetDirectory.getChildren(), new Function<VirtualFile, StudyItem>() {
                                   @Override
                                   public StudyItem fun(VirtualFile file) {
                                     return finalTargetLesson.getTask(file.getName());
                                   }
                                 },
                                 newItemIndex - 1, EduNames.TASK, 1);

    taskToMove.setIndex(newItemIndex);
    taskToMove.setLesson(targetLesson);
    targetLesson.getTaskList().add(taskToMove);
    Collections.sort(targetLesson.getTaskList(), EduUtils.INDEX_COMPARATOR);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          //moving file to the same directory leads to exception
          if (!targetDirectory.equals(sourceLessonDir)) {
            sourceDirectory.getVirtualFile().move(this, targetDirectory);
          }
          sourceDirectory.getVirtualFile().rename(this, EduNames.TASK + newItemIndex);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  public boolean tryToMove(PsiElement element,
                           Project project,
                           DataContext dataContext,
                           @Nullable PsiReference reference,
                           Editor editor) {
    return true;
  }
}
