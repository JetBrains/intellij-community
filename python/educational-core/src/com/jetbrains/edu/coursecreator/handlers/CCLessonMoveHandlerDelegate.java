package com.jetbrains.edu.coursecreator.handlers;

import com.intellij.ide.IdeView;
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
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CCMoveStudyItemDialog;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class CCLessonMoveHandlerDelegate extends MoveHandlerDelegate {

  private static final Logger LOG = Logger.getInstance(CCLessonMoveHandlerDelegate.class);

  @Override
  public boolean canMove(DataContext dataContext) {
    if (CommonDataKeys.PSI_FILE.getData(dataContext) != null) {
      return false;
    }
    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return false;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0 || directories.length > 1) {
      return false;
    }

    final PsiDirectory sourceDirectory = directories[0];
    return CCUtils.isLessonDir(sourceDirectory);
  }

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    if (elements.length > 0 && elements[0] instanceof PsiDirectory) {
      return CCUtils.isLessonDir(((PsiDirectory)elements[0]));
    }
    return false;
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
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final PsiDirectory sourceDirectory = (PsiDirectory)elements[0];
    final Lesson sourceLesson = course.getLesson(sourceDirectory.getName());
    final Lesson targetLesson = course.getLesson(((PsiDirectory)targetContainer).getName());
    if (targetLesson == null) {
      Messages.showInfoMessage("Lessons can be moved only to other lessons", "Incorrect Target For Move");
      return;
    }
    final CCMoveStudyItemDialog dialog = new CCMoveStudyItemDialog(project, EduNames.LESSON, targetLesson.getName());
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
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
    final VirtualFile[] lessonDirs = project.getBaseDir().getChildren();
    final Function<VirtualFile, StudyItem> getStudyItem = file -> course.getLesson(file.getName());

    int sourceLessonIndex = sourceLesson.getIndex();
    sourceLesson.setIndex(-1);
    CCUtils.updateHigherElements(lessonDirs, getStudyItem, sourceLessonIndex, EduNames.LESSON, -1);

    final int newItemIndex = targetLesson.getIndex() + dialog.getIndexDelta();

    CCUtils.updateHigherElements(lessonDirs, getStudyItem,
                                 newItemIndex - 1, EduNames.LESSON, 1);

    sourceLesson.setIndex(newItemIndex);
    course.sortLessons();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          sourceDirectory.getVirtualFile().rename(this, EduNames.LESSON + newItemIndex);
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
    return CCUtils.isCourseCreator(project);
  }
}
