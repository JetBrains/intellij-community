package com.jetbrains.edu.learning;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.checker.StudyTestRunner;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

public class PyStudyTestRunner extends StudyTestRunner {
  private static final String PYTHONPATH = "PYTHONPATH";
  private static final Logger LOG = Logger.getInstance(PyStudyTestRunner.class.getName());

  PyStudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    super(task, taskDir);
  }

  public Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException {
    final Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    Course course = myTask.getLesson().getCourse();
    StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
    if (manager == null) {
      LOG.info("Language manager is null for " + course.getLanguageById().getDisplayName());
      return null;
    }
    final File testRunner = new File(myTaskDir.getPath(), manager.getTestFileName());
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.withWorkDirectory(myTaskDir.getPath());
    final Map<String, String> env = commandLine.getEnvironment();

    final VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      env.put(PYTHONPATH, courseDir.getPath());
    }
    if (sdk != null) {
      String pythonPath = sdk.getHomePath();
      if (pythonPath != null) {
        commandLine.setExePath(pythonPath);
        commandLine.addParameter(testRunner.getPath());
        File resourceFile = new File(course.getCourseDirectory());
        commandLine.addParameter(resourceFile.getPath());
        commandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
        return commandLine.createProcess();
      }
    }
    return null;
  }
}
