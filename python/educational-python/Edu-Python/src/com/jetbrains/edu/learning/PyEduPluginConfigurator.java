package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.coursecreator.settings.CCSettings;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyEduPluginConfigurator implements EduPluginConfigurator {
  public static final String PYTHON_3 = "3.x";
  public static final String PYTHON_2 = "2.x";
  private static final String TESTS_PY = "tests.py";

  @NotNull
  @Override
  public String getTestFileName() {
    return TESTS_PY;
  }

  @Override
  public PsiDirectory createTask(@NotNull Project project,
                                 @NotNull StudyItem item,
                                 @Nullable IdeView view,
                                 @NotNull PsiDirectory parentDirectory,
                                 @NotNull Course course) {
    final Ref<PsiDirectory> taskDirectory = new Ref<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
      String taskDirName = EduNames.TASK + item.getIndex();
      taskDirectory.set(DirectoryUtil.createSubdirectories(taskDirName, parentDirectory, "\\/"));
      if (taskDirectory.get() != null) {
        createTaskContent(project, view, taskDirectory.get());
      }
    });
    return taskDirectory.get();
  }

  @Override
  public void createTaskContent(@NotNull Project project,
                                @Nullable IdeView view,
                                PsiDirectory taskDirectory) {
    StudyUtils.createFromTemplate(project, taskDirectory, "task.py", view, false);
    StudyUtils.createFromTemplate(project, taskDirectory, TESTS_PY, view, false);
    StudyUtils.createFromTemplate(project, taskDirectory,
                                  StudyUtils.getTaskDescriptionFileName(CCSettings.getInstance().useHtmlAsDefaultTaskFormat()), view,
                                  false);
  }
}
