package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Lesson;
import com.jetbrains.edu.courseFormat.StudyOrderable;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CCCreateStudyItemDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class CCCreateLesson extends DumbAwareAction {
  public static final String TITLE = "Create New " + EduNames.LESSON_TITLED;

  public CCCreateLesson() {
    super(EduNames.LESSON_TITLED, TITLE, PlatformIcons.DIRECTORY_CLOSED_ICON);
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
    createLesson(view, project, directory.getVirtualFile(), course);
  }

  @Nullable
  public static PsiDirectory createLesson(@Nullable IdeView view, @NotNull final Project project,
                                          @NotNull final VirtualFile directory, @NotNull final Course course) {
    Lesson lesson = getLesson(directory, project, course, view);
    if (lesson == null) {
      return null;
    }
    VirtualFile courseDir = project.getBaseDir();
    int lessonIndex = lesson.getIndex();
    CCUtils.updateHigherElements(courseDir.getChildren(), new Function<VirtualFile, StudyOrderable>() {
      @Override
      public StudyOrderable fun(VirtualFile file) {
        return course.getLesson(file.getName());
      }
    }, lessonIndex - 1, EduNames.LESSON, 1);
    course.addLesson(lesson);
    Collections.sort(course.getLessons(), EduUtils.INDEX_COMPARATOR);
    return createLessonDir(project, lessonIndex, view);
  }

  @Nullable
  private static Lesson getLesson(@NotNull final VirtualFile sourceDirectory,
                                  @NotNull final Project project,
                                  @NotNull final Course course,
                                  @Nullable IdeView view) {

    VirtualFile courseDir = project.getBaseDir();
    String lessonName;
    int lessonIndex;
    if (sourceDirectory.equals(courseDir)) {
      lessonIndex = course.getLessons().size() + 1;
      String suggestedName = EduNames.LESSON + lessonIndex;
      lessonName = view == null ? suggestedName : Messages.showInputDialog("Name:", TITLE, null, suggestedName, null);
    } else {
      Lesson sourceLesson = course.getLesson(sourceDirectory.getName());
      if (sourceLesson == null) {
        return null;
      }
      final int index = sourceLesson.getIndex();
      CCCreateStudyItemDialog dialog = new CCCreateStudyItemDialog(project, EduNames.LESSON, sourceLesson.getName(), index);
      dialog.show();
      if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
        return null;
      }
      lessonName = dialog.getName();
      lessonIndex = index + dialog.getIndexDelta();
    }
    if (lessonName == null) {
      return null;
    }
    return createAndInitLesson(course, lessonName, lessonIndex);
  }

  @NotNull
  private static Lesson createAndInitLesson(@NotNull final Course course, @NotNull String lessonName, int lessonIndex) {
    Lesson lesson = new Lesson();
    lesson.setName(lessonName);
    lesson.setCourse(course);
    lesson.setIndex(lessonIndex);
    return lesson;
  }

  @Nullable
  public static PsiDirectory createLessonDir(@NotNull final Project project, final int index, final IdeView view) {
    final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
    final PsiDirectory[] lessonDirectory = new PsiDirectory[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        lessonDirectory[0] = DirectoryUtil.createSubdirectories(EduNames.LESSON + index, projectDir, "\\/");
      }
    });
    if (lessonDirectory[0] != null) {
      if (view != null) {
        view.selectElement(lessonDirectory[0]);
      }
    }
    return lessonDirectory[0];
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
    if (directory != null && course != null && course.getLesson(directory.getName()) != null) {
      EduUtils.enableAction(event, true);
      return;
    }
    EduUtils.enableAction(event, false);
  }
}