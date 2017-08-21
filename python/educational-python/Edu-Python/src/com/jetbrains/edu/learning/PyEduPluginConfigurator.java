package com.jetbrains.edu.learning;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.jetbrains.edu.learning.checker.PyStudyTaskChecker;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.courseGeneration.StudyGenerator;
import com.jetbrains.edu.learning.newproject.EduCourseProjectGenerator;
import com.jetbrains.python.PythonModuleTypeBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PyEduPluginConfigurator implements EduPluginConfigurator {
  public static final String PYTHON_3 = "3.x";
  public static final String PYTHON_2 = "2.x";
  private static final String TESTS_PY = "tests.py";
  private static final Logger LOG = Logger.getInstance(PyEduPluginConfigurator.class);
  private static final String COURSE_NAME = "Introduction to Python.zip";
  private static final String TASK_PY = "task.py";
  private PyStudyDirectoryProjectGenerator myGenerator = new PyStudyDirectoryProjectGenerator();

  @NotNull
  @Override
  public String getTestFileName() {
    return TESTS_PY;
  }

  @NotNull
  @Override
  public String getStepikDefaultLanguage() {
    return "python3";
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
      if (taskDirectory.isNull()) return;

      if (course.isAdaptive() && !task.getTaskFiles().isEmpty()) {
        createTaskFilesFromText(task, taskDirectory.get());
      }
      else {
        createFilesFromTemplates(project, view, taskDirectory.get());
      }
    });
    return taskDirectory.get();
  }

  private static void createTaskFilesFromText(@NotNull Task task, @Nullable PsiDirectory taskDirectory) {
    if (taskDirectory == null) {
      LOG.warn("Task directory is null. Cannot create task files");
      return;
    }

    for (TaskFile file : task.getTaskFiles().values()) {
      try {
        StudyGenerator.createTaskFile(taskDirectory.getVirtualFile(), file);
      }
      catch (IOException e) {
        LOG.warn(e.getMessage());
      }
    }
  }

  private static void createFilesFromTemplates(@NotNull Project project,
                                               @Nullable IdeView view,
                                               @NotNull PsiDirectory taskDirectory) {
    StudyUtils.createFromTemplate(project, taskDirectory, TASK_PY, view, false);
    StudyUtils.createFromTemplate(project, taskDirectory, TESTS_PY, view, false);
  }

  @Override
  public boolean excludeFromArchive(@NotNull String path) {
    return path.contains("__pycache__") || path.endsWith(".pyc");
  }

  @Override
  public boolean isTestFile(VirtualFile file) {
    String name = file.getName();
    if (TESTS_PY.equals(name)) {
      return true;
    }
    return name.contains(FileUtil.getNameWithoutExtension(TESTS_PY)) && name.contains(EduNames.SUBTASK_MARKER);
  }

  @Override
  public void createTestsForNewSubtask(@NotNull Project project, @NotNull TaskWithSubtasks task) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    int nextSubtaskIndex = task.getLastSubtaskIndex() + 1;
    String nextSubtaskTestsFileName = getSubtaskTestsFileName(nextSubtaskIndex);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        PsiDirectory taskPsiDir = PsiManager.getInstance(project).findDirectory(taskDir);
        FileTemplate testsTemplate = FileTemplateManager.getInstance(project).getInternalTemplate(TESTS_PY);
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
    return index == 0 ? TESTS_PY : FileUtil.getNameWithoutExtension(TESTS_PY) +
                                              EduNames.SUBTASK_MARKER +
                                              index + "." +
                                              FileUtilRt.getExtension(TESTS_PY);
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
  @NotNull
  public StudyTaskChecker<PyCharmTask> getPyCharmTaskChecker(@NotNull PyCharmTask task, @NotNull Project project) {
    return new PyStudyTaskChecker(task, project);
  }

  @Override
  public List<String> getBundledCoursePaths() {
    @NonNls String jarPath = PathUtil.getJarPathForClass(PyEduPluginConfigurator.class);

    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);

      File pluginBaseDir = jarFile.getParentFile();
      return Collections.singletonList(new File(new File(pluginBaseDir, "courses"), COURSE_NAME).getPath());
    }

    return Collections.singletonList(new File(new File(jarPath, "courses"), COURSE_NAME).getPath());
  }

  @Override
  public EduCourseProjectGenerator getEduCourseProjectGenerator() {
    return myGenerator;
  }

  public ModuleType getModuleType() {
    return PythonModuleTypeBase.getInstance();
  }

  @Override
  public void configureModule(@NotNull Module module) {
    final Project project = module.getProject();
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      final VirtualFile baseDir = project.getBaseDir();
      final String testHelper = EduNames.TEST_HELPER;
      if (baseDir.findChild(testHelper) != null) return;
      final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
      final PsiDirectory projectDir = PsiManager.getInstance(project).findDirectory(baseDir);
      if (projectDir == null) return;
      try {
        FileTemplateUtil.createFromTemplate(template, testHelper, null, projectDir);
      }
      catch (Exception exception) {
        LOG.error("Can't copy test_helper.py " + exception.getMessage());
      }
    });
  }
}
