package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.coursecreator.settings.CCSettings;
import com.jetbrains.edu.learning.actions.StudyCheckAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class PyEduPluginConfigurator implements EduPluginConfigurator {
  public static final String PYTHON_3 = "3.x";
  public static final String PYTHON_2 = "2.x";
  private static final String TESTS_PY = "tests.py";
  private static final Logger LOG = Logger.getInstance(PyEduPluginConfigurator.class);

  @NotNull
  @Override
  public String getTestFileName() {
    return TESTS_PY;
  }

  @Override
  public PsiDirectory createTaskContent(@NotNull Project project,
                                        @NotNull Task task,
                                        @Nullable IdeView view,
                                        @NotNull PsiDirectory parentDirectory,
                                        @NotNull Course course) {
    final Ref<PsiDirectory> taskDirectory = new Ref<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
      String taskDirName = EduNames.TASK + task.getIndex();
      taskDirectory.set(DirectoryUtil.createSubdirectories(taskDirName, parentDirectory, "\\/"));
      if (taskDirectory.get() != null) {
        createFilesFromTemplates(project, view, taskDirectory.get());
      }
    });
    return taskDirectory.get();
  }

  private static void createFilesFromTemplates(@NotNull Project project,
                                               @Nullable IdeView view,
                                               @NotNull PsiDirectory taskDirectory) {
    StudyUtils.createFromTemplate(project, taskDirectory, "task.py", view, false);
    StudyUtils.createFromTemplate(project, taskDirectory, TESTS_PY, view, false);
    StudyUtils.createFromTemplate(project, taskDirectory,
                                  StudyUtils.getTaskDescriptionFileName(CCSettings.getInstance().useHtmlAsDefaultTaskFormat()), view,
                                  false);
  }

  @Override
  public boolean excludeFromArchive(File pathname) {
    String name = pathname.getName();
    return name.contains("__pycache__") || name.contains(".pyc");
  }

  @Override
  public boolean isTestFile(VirtualFile file) {
    String name = file.getName();
    if (EduNames.TESTS_FILE.equals(name)) {
      return true;
    }
    return name.contains(FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE)) && name.contains(EduNames.SUBTASK_MARKER);
  }

  @Override
  public void createTestsForNewSubtask(@NotNull Project project, @NotNull Task task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    int nextSubtaskIndex = task.getLastSubtaskIndex() + 1;
    String nextSubtaskTestsFileName = getSubtaskTestsFileName(nextSubtaskIndex);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        PsiDirectory taskPsiDir = PsiManager.getInstance(project).findDirectory(taskDir);
        FileTemplate testsTemplate = FileTemplateManager.getInstance(project).getInternalTemplate(EduNames.TESTS_FILE);
        if (taskPsiDir == null || testsTemplate == null) {
          return;
        }
        FileTemplateUtil.createFromTemplate(testsTemplate, nextSubtaskTestsFileName, null, taskPsiDir);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  @NotNull
  public static String getSubtaskTestsFileName(int index) {
    return index == 0 ? EduNames.TESTS_FILE : FileUtil.getNameWithoutExtension(EduNames.TESTS_FILE) +
                                              EduNames.SUBTASK_MARKER +
                                              index + "." +
                                              FileUtilRt.getExtension(EduNames.TESTS_FILE);
  }

  @NotNull
  @Override
  public String getDefaultHighlightingMode() {
    return "python";
  }

  @NotNull
  @Override
  public String getLanguageScriptUrl() {
    return getClass().getResource("/python.js").toExternalForm();
  }

  @Override
  public StudyCheckAction getCheckAction() {
    return new PyStudyCheckAction();
  }
}
