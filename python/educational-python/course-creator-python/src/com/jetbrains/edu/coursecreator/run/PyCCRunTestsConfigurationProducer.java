package com.jetbrains.edu.coursecreator.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyCCRunTestsConfigurationProducer extends RunConfigurationProducer<PyCCRunTestConfiguration> {
  protected PyCCRunTestsConfigurationProducer() {
    super(PyCCRunTestsConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(PyCCRunTestConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    Project project = context.getProject();
    if (!CCUtils.isCourseCreator(project)) {
      return false;
    }

    String testsPath = getTestPath(context);
    if (testsPath == null) {
      return false;
    }
    VirtualFile testsFile = LocalFileSystem.getInstance().findFileByPath(testsPath);
    if (testsFile == null) {
      return false;
    }

    String generatedName = generateName(testsFile, project);
    if (generatedName == null) {
      return false;
    }

    configuration.setPathToTest(testsPath);
    configuration.setName(generatedName);
    return true;
  }

  @Nullable
  private static String generateName(@NotNull VirtualFile testsFile, @NotNull Project project) {
    VirtualFile taskDir = StudyUtils.getTaskDir(testsFile);
    if (taskDir == null) {
      return null;
    }
    Task task = StudyUtils.getTask(project, taskDir);
    if (task == null) {
      return null;
    }
    return task.getLesson().getName() + "/" + task.getName();
  }

  @Nullable
  private static String getTestPath(@NotNull ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) {
      return null;
    }
    VirtualFile file = location.getVirtualFile();
    if (file == null) {
      return null;
    }
    VirtualFile taskDir = StudyUtils.getTaskDir(file);
    if (taskDir == null) {
      return null;
    }

    Task task = StudyUtils.getTask(location.getProject(), taskDir);
    if (task == null) {
      return null;
    }
    String testsFileName = EduNames.TESTS_FILE;
    String taskDirPath = FileUtil.toSystemDependentName(taskDir.getPath());
    String testsPath = taskDir.findChild(EduNames.SRC) != null ?
                       FileUtil.join(taskDirPath, EduNames.SRC, testsFileName) :
                       FileUtil.join(taskDirPath, testsFileName);
    String filePath = FileUtil.toSystemDependentName(file.getPath());
    return filePath.equals(testsPath) ? testsPath : null;
  }

  @Override
  public boolean isConfigurationFromContext(PyCCRunTestConfiguration configuration, ConfigurationContext context) {
    String path = getTestPath(context);
    if (path == null) {
      return false;
    }
    return path.equals(configuration.getPathToTest());
  }
}
