package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.format.Lesson;

public class CCCreateLesson extends DumbAwareAction {
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
    final int size = course.getLessons().size();
    final String lessonName = Messages.showInputDialog("Name:", "Lesson Name", null, "lesson" + (size+1), null);
    if (lessonName == null) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        createLesson(directory, size + 1, lessonName, view, course);
      }
    });
  }

  @Nullable
  public static PsiDirectory createLesson(@NotNull final  PsiDirectory projectDir, int index, String name, final IdeView view,
                                  @NotNull final Course course) {
    String lessonFolderName = "lesson" + index;
    final PsiDirectory lessonDirectory = DirectoryUtil.createSubdirectories("lesson" + index, projectDir, "\\/");
    if (lessonDirectory != null) {
      if (view != null) {
        view.selectElement(lessonDirectory);
      }
      final Lesson lesson = new Lesson(name != null ? name : lessonFolderName);
      lesson.setIndex(index);
      course.addLesson(lesson, lessonDirectory);
    }
    return lessonDirectory;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory != null && !project.getBaseDir().equals(directory.getVirtualFile())) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(true);

  }
}