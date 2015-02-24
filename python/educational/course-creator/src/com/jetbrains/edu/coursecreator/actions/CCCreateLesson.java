package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.coursecreator.CCProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class CCCreateLesson extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCCreateLesson.class.getName());

  public CCCreateLesson() {
    super("Lesson", "Create new Lesson", PlatformIcons.DIRECTORY_CLOSED_ICON);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null) {
      return;
    }
    //"Create Lesson" invoked from project root creates new lesson as last lesson
    if (directory.getVirtualFile().equals(project.getBaseDir())) {
      final int size = course.getLessons().size();
      createLesson(project, size + 1, view);
      return;
    }
    //"Create Lesson" invoked from any of lesson directories creates new lesson as next lesson
    Lesson lesson = course.getLesson(directory.getName());
    if (lesson != null) {
      int index = lesson.getIndex();
      List<Lesson> lessons = course.getLessons();
      int lessonNum = lessons.size();
      for (int i = lessonNum; i >= index + 1; i--) {
        updateLesson(project, EduNames.LESSON, i);
      }
      final PsiDirectory parent = directory.getParent();
      if (parent == null) {
        return;
      }
      createLesson(project, index + 1, view);
    }
  }

  private static void createLesson(@NotNull final Project project,
                                   final int index,
                                   final IdeView view) {
    final String lessonName = Messages.showInputDialog("Name:", "Lesson Name", null, EduNames.LESSON + index, null);
    if (lessonName == null) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        createLessonDir(project, index, lessonName, view);
      }
    });
  }

  private void updateLesson(@NotNull final Project project, final String lessonDirName, int i) {
    final VirtualFile lessonDir = project.getBaseDir().findChild(lessonDirName + i);
    if (lessonDir == null) {
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    Lesson l = service.getCourse().getLesson(lessonDir.getName());
    if (l == null) {
      return;
    }
    l.setIndex(l.getIndex() + 1);
    final int next = i + 1;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          lessonDir.rename(this, lessonDirName + next);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });
  }

  @Nullable
  public static PsiDirectory createLessonDir(@NotNull final Project project, int index, String name, final IdeView view) {
    String lessonFolderName = EduNames.LESSON + index;
    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
    final PsiDirectory lessonDirectory = DirectoryUtil.createSubdirectories(EduNames.LESSON + index, projectDir, "\\/");
    final CCProjectService service = CCProjectService.getInstance(project);
    if (lessonDirectory != null) {
      if (view != null) {
        view.selectElement(lessonDirectory);
      }
      final Lesson lesson = new Lesson();
      lesson.setName(name != null ? name : lessonFolderName);
      lesson.setIndex(index);
      service.getCourse().addLesson(lesson);
    }
    return lessonDirectory;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (project == null || view == null) {
      EduUtils.enableAction(event, false);
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      EduUtils.enableAction(event, false);
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory != null && project.getBaseDir().equals(directory.getVirtualFile())) {
      EduUtils.enableAction(event, true);
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    Course course = service.getCourse();

    if (directory != null && course != null && project.getBaseDir().equals(directory)) {
      EduUtils.enableAction(event, false);
    }
    EduUtils.enableAction(event, false);
  }
}